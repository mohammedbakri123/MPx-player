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

# youtubedl-android (issue #324): the AAR ships no consumer ProGuard rules.
# Jackson parses VideoInfo/VideoFormat via reflection, and the library's
# entry points are reached from the app by name — keep the library and the
# Jackson databind/core it parses with. The minified build is the proof
# step: a stripped VideoInfo would only fail at download time on device.
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-keep class com.fasterxml.jackson.core.** { *; }

# commons-compress (issue #356): youtubedl-android unzips its python env
# with org.apache.commons.compress (ZipUtils.unzip -> ZipFile). The zip
# extra-field registry (ExtraFieldUtils.<clinit>) instantiates the known
# extra-field classes (AsiExtraField, ...) reflectively via
# Class.newInstance() — their no-arg constructors are never called from
# code, so R8 stripped them and the minified build crashed at downloader
# init with "AsiExtraField is not a concrete class". Keep the whole
# library: it is small and the failure mode is a launch crash, not a
# build error. youtubedl_common (ZipUtils itself) is kept too — same
# reflective path, same stakes.
-keep class org.apache.commons.compress.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }

# commons-compress references its optional xz dependency
# (org.tukaani.xz, used only by the 7z/XZ archivers) which is not shipped.
# R8 fails the minified build on the missing classes — silence with the
# wildcard form of the rules R8 itself generated (missing_rules.txt).
# No keep needed: none of this code is reachable from the app (only
# ZipFile is used, for unzipping the python env).
-dontwarn org.tukaani.xz.**
-keepattributes *Annotation*,Signature,EnclosingMethod

# Jackson (issue #324, youtubedl-android) references three JDK classes Android
# never ships: java.beans.ConstructorProperties / java.beans.Transient
# (Java7SupportImpl) and org.w3c.dom.bootstrap.DOMImplementationRegistry
# (DOMSerializer). R8 fails the minified build on them — silence with the
# exact rules R8 itself generated (missing_rules.txt). No keep needed:
# none of this code is reachable from the app's JSON parsing.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# smbj (issue #78) pulls three transitive deps Android never uses at runtime:
# javax.el (mbassy's EL filter), org.ietf.jgss (Kerberos GSS-API — smbj uses
# NTLM on Android) and slf4j's StaticLoggerBinder (no binding shipped).
# R8 fails the minified build on the missing classes, so silence them with
# the exact rules R8 itself generated (missing_rules.txt). No keep needed:
# none of this code is reachable from the app.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
