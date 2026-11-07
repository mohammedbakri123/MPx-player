# Contributing to MPx Player

Thanks for helping. MPx is a small project with one maintainer, so small, focused contributions land fastest.

## First: how this project works

- **Stack:** native Kotlin, Jetpack Compose, Material 3 Expressive, Room, mpv (via `mpv-android-lib`) for playback, yt-dlp + bundled ffmpeg / aria2c for downloads.
- **Branch:** `master` is the current branch. Open PRs against `master`.
- **Releases** go out when a goal is reached, verified by **green builds only** — there is no emulator here and the maintainer does not test APKs on his phone. A green build is the gate; on-device proof comes from users in Discussions.
- **Language:** code and issues in English so contributors can follow them. Users can give feedback in Arabic or English in Discussions / Telegram.

## Ways to help (no code needed)

- Try the latest release and report what breaks — device, Android version, MPx version, and steps to reproduce.
- Answer a question in Discussions if you know the answer.
- Translate: strings live in `app/src/main/res/values/strings.xml`, with one `values-<lang>/strings.xml` per language. Keep every key present in every locale — a missing key breaks the build parity check.
- Take real phone screenshots for the README (see `docs/screenshots/README.md`).

## Good first issues

Look for the [`good first issue`](https://github.com/mohammedbakri123/MPx-player/labels/good%20first%20issue) label. Good first PRs are: a small bug fix, a missing translation, a UI polish with a before/after screenshot.

If an issue has no label and looks small, comment "I'd like to take this" and wait for a maintainer reply before starting — it avoids two people doing the same work.

## Before you write code

1. Search open issues — your bug may already be filed.
2. For anything bigger than a typo fix, open an issue first and describe the change. Big PRs with no prior discussion are likely to be closed.
3. Keep the PR to **one thing**. A PR that fixes a bug *and* refactors a screen *and* adds a feature will sit unreviewed.

## Build

Requires JDK 17 and the Android SDK (platform 34, build-tools).

```sh
git clone https://github.com/mohammedbakri123/MPx-player.git
cd MPx-player
./gradlew --no-daemon assembleDebug
```

Build quirks that have bitten this project before — please respect them:

- **mpv AAR / mavenLocal:** Gradle running as root resolves `mavenLocal()` to `/root/.m2/repository`, not `~/.m2/repository`. Pass `-Dmaven.repo.local=$HOME/.m2/repository` when the build needs the mpv AAR. A fresh Gradle home often cannot fetch the mpv AAR at all — reuse a Gradle home that already has it cached.
- **Prefer `--no-daemon`.**
- **Debug ≠ release.** Debug builds don't run R8. If your change touches anything loaded by reflection, serialisation, or runtime registration (downloader libraries, mpv, codecs), also build the minified release variant and check R8's output keeps what it needs. v0.3.5 shipped a launch crash because R8 stripped `commons-compress` — don't repeat it.
- **Strings:** every new user-facing string needs an entry in **all 9 locales** (`values`, `values-ar`, `values-de`, `values-es`, `values-fr`, `values-hi`, `values-pt`, `values-ru`, `values-zh`). Same key, same count.
- **No placeholders shipped as features.** If a screen isn't built, say so in the code/UI — don't fake it.

## PR checklist

- [ ] One change, one PR, against `master`
- [ ] Debug build passes (`./gradlew --no-daemon assembleDebug`)
- [ ] If it touches reflection / R8 / startup: minified release build also passes, and you checked the kept classes
- [ ] New strings exist in all 9 locales
- [ ] Screenshots or a short screen recording for UI changes
- [ ] Issue number in the PR title or description (e.g. "Fix #359: …")

## Hard lines — please don't

- **Do not copy MX Player's source, binaries, assets, icons, strings, or branding.** Copying *layout ideas* in MPx's own code is fine and is how the project works; lifting MX proprietary material is not, in any build, even private or renamed.
- **Do not embed MX's proprietary engine** in MPx in any form.
- Do not add ads, tracking, or analytics without discussing it first — the project promises none.
- Do not add a new dependency without saying why in the PR. The APK is already ~87 MB; every MB needs a reason.

## Code style

- Follow the existing code around the change. Kotlin, Compose idioms, Material 3.
- Keep the UX law in mind: true black in dark mode, one accent (identity blue) used sparingly, clean uncrowded screens, back = exactly one step, dock tabs switch by tap only.

## Questions?

Ask in [Discussions](https://github.com/mohammedbakri123/MPx-player/discussions) — that's faster than opening an issue for a question, and other contributors can see the answer.
