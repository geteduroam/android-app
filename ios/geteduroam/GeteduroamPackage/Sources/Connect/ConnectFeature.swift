import AppAuth
import AuthClient
import ComposableArchitecture
import EAPConfigParser
import EAPConfigurator
import Foundation
import Models
import SwiftUI

public struct Connect: ReducerProtocol {
    public let authClient: AuthClient
    
    public init(authClient: AuthClient = FailingAuthClient()) {
        self.authClient = authClient
    }
    
    public struct State: Equatable {
        public init(institution: Institution, loadingState: LoadingState = .initial) {
            self.institution = institution
            self.loadingState = loadingState
        }
        
        public let institution: Institution
        public var selectedProfileId: Profile.ID?
        
        public var selectedProfile: Profile? {
            if let selectedProfileId {
                // Selected profile
                return institution.profiles.first(where: { $0.id == selectedProfileId })
            } else if let firstDefaultProfile = institution.profiles.first(where: { ($0.default ?? false) == true }) {
                // Otherwise the first default
                return firstDefaultProfile
            } else if let firstProfile = institution.profiles.first {
                // Otherwise the first
                return firstProfile
            } else {
                return nil
            }
        }
        
        public enum LoadingState: Equatable {
            case initial
            case isLoading
            case success
            case failure
        }
        
        var loadingState: LoadingState
        var alert: AlertState<Action>?
    }
    
    public enum Action: Equatable {
        public static func == (lhs: Connect.Action, rhs: Connect.Action) -> Bool {
            switch (lhs, rhs) {
            case (.onAppear, .onAppear):
                return true
            case let (.select(lhs), .select(rhs)):
                return lhs == rhs
            case (.connect, .connect):
                return true
            case let (.connectResponse(lhs), .connectResponse(rhs)):
                return lhs == rhs
            default:
                return false
            }
        }
        
        case onAppear
        case select(Profile.ID)
        case connect
        case connectResponse(TaskResult<Bool>)
        case dismissErrorTapped
        case startAgainTapped
    }
    
    public enum InstitutionSetupError: Error {
        case noProfile
        case missingAuthorizationEndpoint
        case missingTokenEndpoint
        case accessPointConfigurationFailed
    }
    
    public func reduce(into state: inout State, action: Action) -> EffectTask<Action> {
        switch action {
        case .onAppear:
            guard let profile = state.selectedProfile, state.institution.hasSingleProfile else {
                return .none
            }
            // Auto connect if there is only a single profile
            state.loadingState = .isLoading
            return .task {
                await Action.connectResponse(TaskResult<Bool> { try await connect(profile: profile, authClient: authClient) })
            }
            
        case let .select(profileId):
            state.selectedProfileId = profileId
            return .none
            
        case .connect:
            guard let profile = state.selectedProfile else {
                return .none
            }
            state.loadingState = .isLoading
            return .task {
                await Action.connectResponse(TaskResult<Bool> { try await connect(profile: profile, authClient: authClient) })
            }
            
        case .connectResponse(.success(true)):
            state.loadingState = .success
            return .none
            
        case .connectResponse(.success(false)):
            state.loadingState = .initial
            return .none
            
        case let .connectResponse(.failure(error)):
            state.loadingState = .failure
            state.alert = AlertState(
                title: .init("Failed to connect"),
                message: .init((error as NSError).localizedDescription),
                dismissButton: .default(.init("OK"), action: .send(.dismissErrorTapped))
            )
            return .none
            
        case .dismissErrorTapped:
            state.alert = nil
            return .none
            
        case .startAgainTapped:
            return .none
        }
    }
    
    func connect(profile: Profile, authClient: AuthClient) async throws -> Bool {
        let accessToken: String?
        if profile.oauth ?? false {
            guard let authorizationEndpoint = profile.authorization_endpoint else {
                throw InstitutionSetupError.missingAuthorizationEndpoint
            }
            guard let tokenEndpoint = profile.token_endpoint else {
                throw InstitutionSetupError.missingTokenEndpoint
            }
            let configuration = OIDServiceConfiguration(authorizationEndpoint: authorizationEndpoint, tokenEndpoint: tokenEndpoint)

            // Build authentication request
            let clientId = "app.eduroam.geteduroam"
            let scope = "eap-metadata"
            let redirectURL = URL(string: "app.eduroam.geteduroam:/")!
            let codeVerifier = OIDAuthorizationRequest.generateCodeVerifier()
            let codeChallange = OIDAuthorizationRequest.codeChallengeS256(forVerifier: codeVerifier)
            let state = UUID().uuidString
            let nonce = UUID().uuidString
         
            let request = OIDAuthorizationRequest(configuration: configuration, clientId: clientId, clientSecret: nil, scope: scope, redirectURL: redirectURL, responseType: OIDResponseTypeCode, state: state, nonce: nonce, codeVerifier: codeVerifier, codeChallenge: codeChallange, codeChallengeMethod: OIDOAuthorizationRequestCodeChallengeMethodS256, additionalParameters: nil)

            (accessToken, _) = try await authClient
                .startAuth(request: request)
                .tokens()
        } else {
            accessToken = nil
        }
        
        var urlRequest = URLRequest(url: profile.eapconfig_endpoint!)
        urlRequest.httpMethod = "POST"
        // urlRequest.httpBody = "device=apple-mobileconfig".data(using: .utf8)
        if let accessToken {
            urlRequest.allHTTPHeaderFields = ["Authorization": "Bearer \(accessToken)"]
        }

        let (eapConfigData, _) = try await URLSession.shared.data(for: urlRequest)
        
        let accessPoint = try EAPConfigParser.parse(xmlData: eapConfigData)

        let configurator = WifiEapConfigurator()
        
        let success = try await configurator.configure(accessPoint: accessPoint)
        
        guard success else {
            throw InstitutionSetupError.accessPointConfigurationFailed
        }
        
        return true
    }
    
}
