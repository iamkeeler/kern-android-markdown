# Third-party licenses

Kern includes or builds with third-party libraries. This file records the direct dependencies declared in `app/build.gradle.kts` and `gradle/libs.versions.toml`. Their licenses are separate from Kern’s [Apache License 2.0](LICENSE); nothing in Kern’s license changes the rights granted by those third-party licenses.

## Android and Google

- AndroidX Core KTX, Activity Compose, Lifecycle, Navigation 3, Room, DocumentFile, and AndroidX Test — Apache License 2.0 — Google.
- Jetpack Compose UI, Material, Material Icons, and Compose tooling — Apache License 2.0 — Google.
- Firebase Analytics and Crashlytics — Apache License 2.0 — Google.

## Kotlin and JetBrains

- Kotlin — Apache License 2.0 — JetBrains.
- Kotlin Coroutines — Apache License 2.0 — JetBrains.
- Kotlin Serialization — Apache License 2.0 — JetBrains.
- Kotlinx Collections Immutable — Apache License 2.0 — JetBrains.

## Other direct dependencies

- Coil Compose and Coil Network — Apache License 2.0 — Coil contributors.
- JUnit 4 — Eclipse Public License 1.0.
- Robolectric — MIT License.
- MockK — Apache License 2.0.
- Turbine — Apache License 2.0.
- KSP — Apache License 2.0.
- Fastlane (release automation) — MIT License.

Transitive dependencies may carry additional notices. Release artifacts should preserve the notices supplied by their respective dependency distributions. When adding a dependency, verify its license from the project’s official repository or published artifact metadata and update this file if it is a direct dependency.
