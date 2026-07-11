# Play Data Safety draft — Kern

Status: draft guidance only. Final answers must match the production build uploaded to Play Console.

## Evidence reviewed

- Source manifest: `app/src/main/AndroidManifest.xml`
- Merged release manifest: `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`
- Gradle dependencies: `app/build.gradle.kts`
- Source search for explicit analytics/permissions/API usage

## Important finding

The source app does not declare dangerous runtime permissions such as camera, microphone, contacts, location, or storage permissions.

However, the release merged manifest currently includes Firebase Analytics / Google Play Services permissions and services from dependencies:

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.WAKE_LOCK
com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE
com.google.android.gms.permission.AD_ID
android.permission.ACCESS_ADSERVICES_ATTRIBUTION
android.permission.ACCESS_ADSERVICES_AD_ID
```

`app/build.gradle.kts` includes:

```kotlin
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.analytics)
```

This likely means the Play Data Safety form must disclose analytics/device identifiers unless Firebase Analytics is removed or explicitly disabled for production.

## Recommended product decision

For the simplest privacy posture, consider removing Firebase Analytics from the production build if it is not needed. That would reduce Data Safety complexity and better match Kern's local-first positioning.

If Firebase Analytics remains, disclose analytics collection accurately.

## Draft answers if Firebase Analytics remains enabled

### Does the app collect or share user data?

Likely answer: **Yes**

Reason: Firebase Analytics may collect app interactions, device identifiers, diagnostics, and approximate technical/device information depending on configuration.

### Data types likely involved

Check the exact Firebase configuration before final submission, but likely categories are:

| Play category | Likely answer | Notes |
|---|---:|---|
| App activity | Yes | App interactions / events if Firebase Analytics is active |
| App info and performance | Yes | Crash/performance/diagnostic-adjacent technical data may be collected by Google SDKs depending on enabled services |
| Device or other IDs | Yes | Merged manifest includes `AD_ID` / ad services ID permissions |
| Files and docs | No | Kern is local-first; do not upload user markdown content unless a future feature changes this |
| Location | No | No location permission found |
| Contacts | No | No contacts permission found |
| Photos/videos | No, unless user explicitly exports/shares files through system UI | No media library access permission found |
| Audio | No | No microphone permission found |
| Calendar | No | No calendar permission found |
| Health/fitness | No | Not applicable |
| Financial/payment | No | Not applicable |

### Is collected data encrypted in transit?

Likely answer: **Yes** for Firebase/Google SDK traffic.

### Can users request deletion?

If Firebase Analytics remains, provide a contact path:

```text
iamkeeler@gmail.com
```

and document what can and cannot be deleted, given analytics events may be aggregated/pseudonymous.

### Is collection optional?

If Firebase Analytics is enabled by default with no in-app opt-out, answer as **No** for optionality.

## Draft answers if Firebase Analytics is removed

If the production build removes Firebase Analytics and no other network telemetry SDK remains:

- Data collection: likely **No**
- Files and documents: **not collected**
- Account data: **not collected**
- Location/contacts/photos/audio: **not collected**
- Device IDs: **not collected by Kern**

Still verify the final merged release manifest before submitting.

## Verification command before final Play submission

```bash
./gradlew clean bundleRelease
cat app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
```

Then check the merged manifest for:

```text
uses-permission
firebase
analytics
AD_ID
```

## Current recommendation

Because Kern's product story is local-first and privacy-forward, the cleanest Play path is to remove Firebase Analytics unless there is a specific launch metric requirement. If analytics is needed for internal testing only, gate it by build type or remove it from release before public launch.
