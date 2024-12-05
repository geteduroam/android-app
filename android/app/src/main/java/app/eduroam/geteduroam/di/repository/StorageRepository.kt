package app.eduroam.geteduroam.di.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.models.Configuration
import app.eduroam.geteduroam.models.ConfigSource
import app.eduroam.geteduroam.organizations.ConfiguredOrganization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import timber.log.Timber
import java.io.IOException

class StorageRepository(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(
        name = "oauth_settings"
    )

    val authState: Flow<AuthState?> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Timber.w(exception, "Error reading preferences.")
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }.map { preferences ->
        val authState = preferences[PreferencesKeys.CURRENT_AUTHSTATE]
        authState?.let {
            AuthState.jsonDeserialize(it)
        }
    }

    val isAuthorized: Flow<Boolean> =
        authState.map { it != null && it.isAuthorized && !it.needsTokenRefresh }

    val authRequest: Flow<AuthorizationRequest?> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Timber.w(exception, "Error reading preferences.")
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }.map { preferences ->
        val authRequest = preferences[PreferencesKeys.CURRENT_AUTHREQUEST]
        authRequest?.let {
            AuthorizationRequest.jsonDeserialize(it)
        }
    }

    val clientId: Flow<String?> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Timber.w(exception, "Error reading preferences.")
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }.map { preferences ->
        preferences[PreferencesKeys.CLIENT_ID]
    }

    val lastKnownConfigHash: Flow<Int?> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Timber.w(exception, "Error reading preferences.")
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }.map { preferences ->
        preferences[PreferencesKeys.LAST_KNOWN_CONFIG_HASH]
    }

    val configuredOrganization: Flow<ConfiguredOrganization?> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Timber.w(exception, "Error reading preferences.")
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }.map { preferences ->
        val id = preferences[PreferencesKeys.CONFIGURED_ORGANIZATION_ID]
        val name = preferences[PreferencesKeys.CONFIGURED_ORGANIZATION_NAME]
        val source = preferences[PreferencesKeys.CONFIGURED_ORGANIZATION_SOURCE]
        val country = preferences[PreferencesKeys.CONFIGURED_ORGANIZATION_COUNTRY]
        if (id != null && source != null) {
            try {
                ConfiguredOrganization(
                    source = ConfigSource.valueOf(source),
                    id = id,
                    name = name,
                    country = country
                )
            } catch (ex: Exception) {
                // Source might not exist?
                null
            }
        } else {
            null
        }
    }
    
    val profileExpiryTimestampMs: Flow<Long?> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Timber.w(exception, "Error reading preferences.")
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }.map { preferences ->
        preferences[PreferencesKeys.CONFIGURED_PROFILE_EXPIRY_TIMESTAMP_MS]
    }

    val configuredProfileLastConfig: Flow<EAPIdentityProviderList?> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Timber.w(exception, "Error reading preferences.")
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }.map { preferences ->
        val savedConfig = preferences[PreferencesKeys.CONFIGURED_PROFILE_LAST_CONFIG] ?: return@map null
        try {
            return@map Json.decodeFromString(savedConfig)
        } catch (ex: Exception) {
            return@map null
        }
    }

    suspend fun clearInvalidAuth() = context.dataStore.edit { settings ->
        settings.remove(PreferencesKeys.CURRENT_AUTHREQUEST)
        settings.remove(PreferencesKeys.CURRENT_AUTHSTATE)
    }

    suspend fun clearAll() = context.dataStore.edit { settings ->
        settings.clear()
    }

    suspend fun saveCurrentAuthState(authState: AuthState?) = context.dataStore.edit { settings ->
        settings[PreferencesKeys.CURRENT_AUTHSTATE] =
            authState?.jsonSerializeString() ?: settings.remove(PreferencesKeys.CURRENT_AUTHSTATE)
    }

    suspend fun saveCurrentAuthRequest(authRequest: AuthorizationRequest?) =
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.CURRENT_AUTHREQUEST] = authRequest?.jsonSerializeString()
                ?: settings.remove(PreferencesKeys.CURRENT_AUTHREQUEST)
        }

    suspend fun saveClientId(clientId: String) = context.dataStore.edit { settings ->
        settings[PreferencesKeys.CLIENT_ID] = clientId
    }

    suspend fun acceptNewConfiguration(configHash: Int) = context.dataStore.edit { settings ->
        settings[PreferencesKeys.LAST_KNOWN_CONFIG_HASH] = configHash
    }

    suspend fun isAuthenticatedForConfig(config: Configuration): Boolean {
        val lastKnownHash = lastKnownConfigHash.first()
        return config.hashCode() == lastKnownHash && isAuthorized.first()
    }

    suspend fun saveConfigForStatusScreen(
        configuredOrganization: ConfiguredOrganization,
        expiryTimestampMs: Long?,
        config: EAPIdentityProviderList
    ) {
        context.dataStore.edit { settings ->
            settings[PreferencesKeys.CONFIGURED_ORGANIZATION_ID] = configuredOrganization.id
            settings[PreferencesKeys.CONFIGURED_ORGANIZATION_SOURCE] = configuredOrganization.source.name
            if (configuredOrganization.name != null) {
                settings[PreferencesKeys.CONFIGURED_ORGANIZATION_NAME] = configuredOrganization.name
            } else {
                settings.remove(PreferencesKeys.CONFIGURED_ORGANIZATION_NAME)
            }
            if (configuredOrganization.country != null) {
                settings[PreferencesKeys.CONFIGURED_ORGANIZATION_COUNTRY] = configuredOrganization.country
            } else {
                settings.remove(PreferencesKeys.CONFIGURED_ORGANIZATION_COUNTRY)
            }
            if (expiryTimestampMs == null) {
                settings.remove(PreferencesKeys.CONFIGURED_PROFILE_EXPIRY_TIMESTAMP_MS)
            } else {
                settings[PreferencesKeys.CONFIGURED_PROFILE_EXPIRY_TIMESTAMP_MS] = expiryTimestampMs
            }
            val serializedConfig = Json.encodeToString(config)
            settings[PreferencesKeys.CONFIGURED_PROFILE_LAST_CONFIG] = serializedConfig
        }
    }

    private object PreferencesKeys {
        val CURRENT_AUTHSTATE = stringPreferencesKey("current_authstate")
        val CURRENT_AUTHREQUEST = stringPreferencesKey("current_authrequest")
        val CLIENT_ID = stringPreferencesKey("currentClientId")

        val LAST_KNOWN_CONFIG_HASH = intPreferencesKey("last_known_configuration_hash")
        val CONFIGURED_ORGANIZATION_ID = stringPreferencesKey("configuredOrganizationId")
        val CONFIGURED_ORGANIZATION_NAME = stringPreferencesKey("configuredOrganizationName")
        val CONFIGURED_ORGANIZATION_COUNTRY = stringPreferencesKey("configuredOrganizationCountry")
        val CONFIGURED_ORGANIZATION_SOURCE = stringPreferencesKey("configuredOrganizationSource")
        val CONFIGURED_PROFILE_LAST_CONFIG = stringPreferencesKey("configuredProfileLastConfig")
        val CONFIGURED_PROFILE_EXPIRY_TIMESTAMP_MS = longPreferencesKey("configuredProfileExpiryTimestampMs")
    }
}