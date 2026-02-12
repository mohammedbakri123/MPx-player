import 'package:flutter/material.dart';
import '../../controller/player_controller.dart';
import 'helpers/bottom_sheet_handle.dart';
import 'helpers/bottom_sheet_title.dart';
import 'helpers/subtitle_toggle.dart';
import 'helpers/font_size_control.dart';
import 'helpers/color_selection.dart';
import 'helpers/background_toggle.dart';
import 'helpers/subtitle_preview.dart';

class SubtitleSettingsSheet extends StatefulWidget {
  final PlayerController controller;

  const SubtitleSettingsSheet({
    super.key,
    required this.controller,
  });

  @override
  State<SubtitleSettingsSheet> createState() => _SubtitleSettingsSheetState();
}

class _SubtitleSettingsSheetState extends State<SubtitleSettingsSheet> {
  void _refresh() {
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.grey.shade900,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const BottomSheetHandle(),
              const BottomSheetTitle(title: 'Subtitle Settings'),
              SubtitleToggle(
                controller: widget.controller,
                onChanged: _refresh,
              ),
              if (widget.controller.subtitlesEnabled) ...[
                const Divider(color: Colors.grey),
                FontSizeControl(
                  controller: widget.controller,
                  onChanged: _refresh,
                ),
                ColorSelection(
                  controller: widget.controller,
                  onChanged: _refresh,
                ),
                BackgroundToggle(
                  controller: widget.controller,
                  onChanged: _refresh,
                ),
                SubtitlePreview(controller: widget.controller),
              ],
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}
