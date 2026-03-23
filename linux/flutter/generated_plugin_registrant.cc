//
//  Generated file. Do not edit.
//

// clang-format off

#include "generated_plugin_registrant.h"

#include <flutter_mpv_libs_linux/flutter_mpv_libs_linux_plugin.h>
#include <flutter_mpv_video/flutter_mpv_video_plugin.h>
#include <flutter_volume_controller/flutter_volume_controller_plugin.h>

void fl_register_plugins(FlPluginRegistry* registry) {
  g_autoptr(FlPluginRegistrar) flutter_mpv_libs_linux_registrar =
      fl_plugin_registry_get_registrar_for_plugin(registry, "FlutterMpvLibsLinuxPlugin");
  flutter_mpv_libs_linux_plugin_register_with_registrar(flutter_mpv_libs_linux_registrar);
  g_autoptr(FlPluginRegistrar) flutter_mpv_video_registrar =
      fl_plugin_registry_get_registrar_for_plugin(registry, "FlutterMpvVideoPlugin");
  flutter_mpv_video_plugin_register_with_registrar(flutter_mpv_video_registrar);
  g_autoptr(FlPluginRegistrar) flutter_volume_controller_registrar =
      fl_plugin_registry_get_registrar_for_plugin(registry, "FlutterVolumeControllerPlugin");
  flutter_volume_controller_plugin_register_with_registrar(flutter_volume_controller_registrar);
}
