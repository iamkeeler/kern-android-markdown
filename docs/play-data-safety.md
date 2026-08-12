# Play Data Safety draft — Kern

Status: draft guidance only. Final answers must match the production build uploaded to Play Console.

## Evidence reviewed

- Source manifest: `app/src/main/AndroidManifest.xml`
- Merged release manifest: `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`
- Gradle dependencies: `app/build.gradle.kts`
- Source search for explicit analytics/permissions/API usage

## Product decision

Firebase Analytics will remain enabled for launch analytics. The Play Data Safety form and privacy policy should disclose analytics/device identifier collection accurately while continuing to state that Kern does not upload user markdown document contents.

## Important finding

The source app does not declare dangerous runtime permissions such as camera, microphone, contacts, location, or storage permissions.

The release merged manifest includes the following permissions required by the app and its Firebase telemetry dependencies:

```text
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.WAKE_LOCK
```

Firebase Analytics transitively declares advertising and install-attribution permissions, but the app removes them during manifest merging because Kern is not an advertising app:

```text
com.google.android.gms.permission.AD_ID          removed
android.permission.ACCESS_ADSERVICES_ATTRIBUTION removed
android.permission.ACCESS_ADSERVICES_AD_ID       removed
com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE removed
```

`app/build.gradle.kts` includes:

```kotlin
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.analytics)
```

This means the Play Data Safety form should disclose analytics/device identifiers.

## Draft answers with Firebase Analytics enabled

### Does the app collect or share user data?

Likely answer: **Yes**

Reason: Firebase Analytics may collect app interactions, device identifiers, diagnostics, and approximate technical/device information depending on configuration.

### Data types likely involved

Check the exact Firebase configuration before final submission, but likely categories are:

| Play category | Likely answer | Notes |
|---|---:|---|
| App activity | Yes | App interactions / events if Firebase Analytics is active |
| App info and performance | Yes | Crash/performance/diagnostic-adjacent technical data may be collected by Google SDKs depending on enabled services |
| Device or other IDs | Review Firebase's actual collection/configuration | The release manifest does not declare advertising ID or AdServices ID permissions; Firebase may still use app/device identifiers for telemetry |
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
gary@attach.design
```

and document what can and cannot be deleted, given analytics events may be aggregated/pseudonymous.

### Is collection optional?

If Firebase Analytics is enabled by default with no in-app opt-out, answer as **No** for optionality.

## Verification command before final Play submission

```bash
./gradlew clean bundleRelease
cat app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml
```

Then check the merged manifest for the retained permissions and confirm the advertising-related permissions are absent:

```text
uses-permission
firebase
ACCESS_NETWORK_STATE
WAKE_LOCK
AD_ID (must be absent)
ACCESS_ADSERVICES (must be absent)
BIND_GET_INSTALL_REFERRER_SERVICE (must be absent)
```

## Current recommendation

Keep Firebase Analytics for launch analytics, disclose the collection in Play Data Safety, and keep the privacy copy clear that analytics does not upload markdown document contents. Do not answer the Play Console advertising ID question "Yes" based solely on Firebase's transitive manifest declaration; verify the final merged release manifest first.
