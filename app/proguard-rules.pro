# Add project specific ProGuard rules here.
# Only targeted keeps are added; no blanket keeps.

# mpv engine (io.github.abdallahmehiz:mpv-android-lib, is.xyz.mpv.*):
# the AAR ships no consumer ProGuard rules, and its Java wrapper is JNI-backed
# (native methods + event/log observer callbacks invoked from libmpv's native
# event loop by class/method name). Without this, R8 may rename or strip the
# wrapper entry points that native code calls into. This is the only
# library-specific keep in this project: Room uses generated code (no rules
# needed).
-keep class is.xyz.mpv.** { *; }

# smbj (issue #78) pulls three transitive deps Android never uses at runtime:
# javax.el (mbassy's EL filter), org.ietf.jgss (Kerberos GSS-API — smbj uses
# NTLM on Android) and slf4j's StaticLoggerBinder (no binding shipped).
# R8 fails the minified build on the missing classes, so silence them with
# the exact rules R8 itself generated (missing_rules.txt). No keep needed:
# none of this code is reachable from the app.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
