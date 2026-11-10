## What does this change?
<!-- One thing, in one or two sentences. Link the issue: Fixes #123 -->

## What did NOT change?
<!-- Say what this PR deliberately leaves alone — behaviour, other screens, the engine, the database. If it is a migration PR, say so: migration PRs move code only. -->

## How was it tested?
- [ ] `./gradlew --no-daemon assembleDebug` passes
- [ ] `./gradlew --no-daemon testDebugUnitTest` passes (or: no unit tests exist yet for this area)
- [ ] Minified release build passes (only if this touches reflection / R8 / startup / downloader libs)
- [ ] Tested on a device / emulator: <!-- device + Android version, or "not tested on device" -->

Build result: <!-- paste the BUILD footer, e.g. "BUILD SUCCESSFUL in 4m 59s, 0 errors" -->

## Screenshots
<!-- For UI changes: before / after, or a short recording. Delete this section if the change has no UI. -->

## Checklist
- [ ] New user-facing strings exist in all 9 locales
- [ ] No MX source, binaries, assets, strings, or branding added
- [ ] No ads, tracking, or new dependency without a reason in the description
