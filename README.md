geteduroam
=================

This is the repository for the a geteduroam KMP project.

# Software

The following software is expected to be available:

- [Android Studio](https://developer.android.com/studio/) (version 4.0 or newer)
- [Visual Studio Code](https://code.visualstudio.com) (version 1.48 or newer)

# Getting Started

Implementation built based on existing documentation:

- [ionic Architecture](https://github.com/geteduroam/ionic-app/blob/architecture/ARCHITECTURE.md)
  The existing ionic app architecture documentation

The app will download a discovery list from `https://discovery.eduroam.app/v1/discovery.json`, and
present a searchable list of all institutions to the user. We filter on name and keywords (no
abbreviations available for now).

The institution might contain multiple profiles, the user gets to choose the profile they want to
use, except if there is only one, then no choice is presented. If the profile has only an
`eapconfig_endpoint` field, the .eap-config is downloaded from that URL, without any authentication.
If the profile also has an `token_endpoint` and `authorization_endpoint`, the application will start
an OAuth Authorization Code Flow, and then download the `.eap-config` file from `eapconfig_endpoint`
and present the access token as a Bearer token in an authorization header.

When the downloaded `.eap-config` file does not contain enough credentials the user must be prompted
for username/password as these are not contained in the file itself. This happens for the
PEAP/MSCHAPv2 method (file contains `<EAPMethod><Type>25</Type></EAPMethod>`
and `<InnerAuthenticationMethod><EAPMethod><Type>
26</Type></EAPMethod></InnerAuthenticationMethod>`). The form for the username/password can impose
requirements to the username set in the .eap-config, such as that the username must end with a
certain realm.

When both an .eap-config and sufficient credentials are available, the native code for configuring
the wifi connection may be called with all relevant fields from the .eap-config and the entered
credentials, if any.

Fetching the institutions list and downloading the `.eap-config` file is done by the shared part of
the code. The file is downloaded and saved in a SQLDelight database (multiplatform support) as a
blob.

The authorization flow and parsing of the xml data must be handled by each platform independently.

- [OAuth flow](https://github.com/geteduroam/lets-wifi/blob/master/API.md#authorization-endpoint)
  The OAuth2 flow in the ionic app (without discovery/generator_endpoint as they are no longer
  required)

# Technical Design

## shared

## Android

[Wi-Fi infrastructure](https://developer.android.com/guide/topics/connectivity/wifi-infrastructure)
"On Android 10 and higher, the Wi-Fi infrastructure includes the Wi-Fi suggestion API for internet
connectivity [...]. On Android 11 and higher, the Settings Intent API enables you to ask the user to
approve adding a saved network or Passpoint configuration."

* Android >= Android 11 (API 30) use an intent with action `ACTION_WIFI_ADD_NETWORKS` (
  available only starting from API 30). Pass the `WifiNetworkSuggestion` including passpoint
  configuration if available to the intent bundle.


* Android == Android 10 (API 29) the Wifi Suggestion API is available, but not
  the `ACTION_WIFI_ADD_NETWORKS` for the intent, therefore must use
  `WifiManager.addNetworkSuggestions(wifiNetworkSuggestions)`. List must include the passpoint
  configuration. To ensure we don't duplicate networks, any previously added suggestions need to be
  removed before hand. These calls may change the networks state and the permission
  for `CHANGE_WIFI_STATE` is required if not granted.

> = Android 12 (API 31)
> extra privacy & security settings:
> setMacRandomizationSetting
> isPasspointTermsAndConditionsSupported()
> isDecoratedIdentitySupported()

NB:
w.r.t. `public void addOrUpdatePasspointConfiguration (PasspointConfiguration config)`
"Compatibility Note: For applications targeting Build.VERSION_CODES.R or above, this API will always
fail and throw IllegalArgumentException."
