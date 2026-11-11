# Add project specific ProGuard rules here.
# Only targeted keeps are added; no blanket keeps.

# mpv engine (io.github.abdallahmehiz:mpv-android-lib, is.xyz.mpv.*):
# the AAR ships no consumer ProGuard rules, and its Java wrapper is JNI-backed
# (native methods + event/log observer callbacks invoked from libmpv's native
# event loop by class/method name). Without this, R8 may rename or strip the
# wrapper entry points that native code calls into. This is the only
# library-specific keep in this project: Room uses generated code (no rules
# needed). It is the only live reflection/JNI surface in the app.
-keep class is.xyz.mpv.** { *; }
