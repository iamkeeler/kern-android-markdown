# Study Companion hinge comparison

Reference reviewed October 29, 2026: local `android-StudyCompanion`, commit `80dda7151898848cc377f5a64947023f65064f2b`, `app/src/main/java/com/attachdesign/innermargin/ui/reader/util/FoldGestureDetector.kt`, its unit tests, and the `ReaderScreen` integration. The reference checkout was read without changes.

The user reports that this implementation triggers consistently on their Pixel 10 Pro Fold. Kern's previous detector was a different interaction, despite using the same Android hinge sensor.

| Behavior | Previous Kern | Study Companion / updated Kern |
| --- | --- | --- |
| Start from flat | >=170°, held 300ms | Observe >=162° |
| Enter crease | <=160°, held 100ms | Observe 95°–158° |
| Return | >=170°, held 100ms | Observe >=165° |
| Flex duration | Up to 2.5 seconds | 150ms–4.5 seconds |
| Closing abort | Below 90° | Below 80° |
| Debounce | 700ms cooldown plus fresh baseline dwell | 1000ms between successful triggers |
| Sensor registration | `SENSOR_DELAY_GAME` | `SENSOR_DELAY_NORMAL` |
| Event handling | Conflate values, resample every 50ms | Process each delivered event |

Kern now follows the reference recognition behavior. It retains its own editor protections: opt-in preference, selected action, readable second-pane sizing, resumed lifecycle, window-focus/modal cancellation, horizontal-fold exclusion, and pointer/IME safety. A recognized action may defer for up to 500ms while input is unsafe; this is separate from recognition and does not create continuous sensor polling. Invalid readings and backwards timestamps still cancel recognition.

The previous collector's conflation and polling could discard a crease between samples; its stationary holds and higher flat thresholds also rejected valid reference traces. Three new unit tests reproduced that mismatch before the update: a 162°/135°/165° trace, a sparse three-event trace without stationary holds, and a comfortable four-second flex. The emulator tests now use the reference's 162° start and 165° return for both reader metrics and the second editor. Additional coverage checks that native-input deferral works without another sensor sample and that expired actions do not fire after input becomes safe.

Verification passed October 29, 2026: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:connectedDebugAndroidTest -x :app:uploadCrashlyticsMappingFileRelease --max-workers=2` completed successfully in 13m 44s. All **304 unit tests** and **59 instrumentation tests** passed, with zero failures/errors/skips; lint reported zero errors and the release APK built. Device: temporary `Kern_Polish_API_36`, emulator-5554, Android 16 API 36, with a real software keyboard. Existing nonfatal R8/Kotlin metadata warnings remain. Log: `/private/tmp/kern-study-hinge-verification.log`.

The three reference-trace tests failed before the detector update (3 failures among 13 recognition tests). No changes were made to Study Companion. Shared test builds retain automatic version/build allocation and packaged-APK/source verification.

 Emulator injection cannot confirm physical sensor delivery or feel; the next Firebase build still needs the user's device check.
