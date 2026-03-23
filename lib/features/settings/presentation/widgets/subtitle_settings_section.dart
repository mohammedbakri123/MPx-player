import 'package:flutter/material.dart';

import '../../services/subtitle_settings_service.dart';
import '../helpers/settings_helpers.dart';
import '../helpers/subtitle_font_helpers.dart';
import 'subtitle_preview.dart';
import 'form_rows.dart';
import 'color_option.dart';

/// Complete subtitle settings section with all controls and preview
class SubtitleSettingsSection extends StatefulWidget {
  const SubtitleSettingsSection({super.key});

  @override
  State<SubtitleSettingsSection> createState() =>
      _SubtitleSettingsSectionState();
}

class _SubtitleSettingsSectionState extends State<SubtitleSettingsSection> {
  late bool _subtitleEnabled;
  late double _subtitleFontSize;
  late Color _subtitleColor;
  late bool _subtitleHasBackground;
  late FontWeight _subtitleFontWeight;
  late String _subtitleFontFamily;
  late double _subtitleBottomPadding;
  late double _subtitleBackgroundOpacity;

  @override
  void initState() {
    super.initState();
    _subtitleEnabled = SubtitleSettingsService.isEnabled;
    _subtitleFontSize = SubtitleSettingsService.fontSize;
    _subtitleColor = SubtitleSettingsService.color;
    _subtitleHasBackground = SubtitleSettingsService.hasBackground;
    _subtitleFontWeight = SubtitleSettingsService.fontWeight;
    _subtitleFontFamily = SubtitleSettingsService.fontFamily;
    _subtitleBottomPadding = SubtitleSettingsService.bottomPadding;
    _subtitleBackgroundOpacity = SubtitleSettingsService.backgroundOpacity;
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedCrossFade(
      duration: const Duration(milliseconds: 180),
      crossFadeState: _subtitleEnabled
          ? CrossFadeState.showSecond
          : CrossFadeState.showFirst,
      firstChild: const SizedBox.shrink(),
      secondChild: Column(
        children: [
          SettingsSliderRow(
            icon: Icons.format_size,
            title: 'Font size',
            value: '${_subtitleFontSize.round()} pt',
            sliderValue: _subtitleFontSize,
            min: 16,
            max: 72,
            onChanged: (value) async {
              await SubtitleSettingsService.setFontSize(value);
              setState(() => _subtitleFontSize = value);
            },
          ),
          SettingsInfoRow(
            icon: Icons.font_download_outlined,
            title: 'Font type',
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: SubtitleFontHelpers.options
                  .map(
                    (option) => ChoiceChip(
                      label: Text(option.label),
                      selected: _subtitleFontFamily == option.family,
                      onSelected: (_) async {
                        await SubtitleSettingsService.setFontFamily(
                            option.family);
                        setState(() => _subtitleFontFamily = option.family);
                      },
                    ),
                  )
                  .toList(),
            ),
          ),
          SettingsInfoRow(
            icon: Icons.format_bold,
            title: 'Font weight',
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FontWeight.w400,
                FontWeight.w500,
                FontWeight.w600,
                FontWeight.w700,
              ]
                  .map(
                    (weight) => ChoiceChip(
                      label: Text(SettingsFontWeightHelpers.getLabel(weight)),
                      selected: _subtitleFontWeight == weight,
                      onSelected: (_) async {
                        await SubtitleSettingsService.setFontWeight(weight);
                        setState(() => _subtitleFontWeight = weight);
                      },
                    ),
                  )
                  .toList(),
            ),
          ),
          SettingsInfoRow(
            icon: Icons.palette_outlined,
            title: 'Text color',
            child: Wrap(
              spacing: 10,
              runSpacing: 10,
              children: SubtitleSettingsService.getDefaultColorOptions()
                  .map(
                    (color) => SettingsColorOption(
                      color: color,
                      isSelected: _subtitleColor.toARGB32() == color.toARGB32(),
                      onTap: () async {
                        await SubtitleSettingsService.setColor(color);
                        setState(() => _subtitleColor = color);
                      },
                    ),
                  )
                  .toList(),
            ),
          ),
          SettingsSwitchRow(
            icon: Icons.rectangle_outlined,
            title: 'Background plate',
            subtitle: 'Add contrast behind subtitle text',
            value: _subtitleHasBackground,
            onChanged: (value) async {
              await SubtitleSettingsService.setHasBackground(value);
              setState(() => _subtitleHasBackground = value);
            },
          ),
          if (_subtitleHasBackground)
            SettingsSliderRow(
              icon: Icons.opacity,
              title: 'Background opacity',
              value: '${(_subtitleBackgroundOpacity * 100).round()}%',
              sliderValue: _subtitleBackgroundOpacity,
              min: 0.2,
              max: 1.0,
              onChanged: (value) async {
                await SubtitleSettingsService.setBackgroundOpacity(value);
                setState(() => _subtitleBackgroundOpacity = value);
              },
            ),
          SettingsSliderRow(
            icon: Icons.vertical_align_bottom,
            title: 'Bottom spacing',
            value: '${_subtitleBottomPadding.round()} px',
            sliderValue: _subtitleBottomPadding,
            min: 12,
            max: 80,
            onChanged: (value) async {
              await SubtitleSettingsService.setBottomPadding(value);
              setState(() => _subtitleBottomPadding = value);
            },
          ),
          const SizedBox(height: 10),
          SubtitlePreview(
            color: _subtitleColor,
            fontSize: _subtitleFontSize,
            fontWeight: _subtitleFontWeight,
            fontFamily: _subtitleFontFamily,
            hasBackground: _subtitleHasBackground,
            backgroundOpacity: _subtitleBackgroundOpacity,
            bottomPadding: _subtitleBottomPadding,
          ),
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: () async {
                await SubtitleSettingsService.resetToDefaults();
                setState(() {
                  _subtitleEnabled = SubtitleSettingsService.isEnabled;
                  _subtitleFontSize = SubtitleSettingsService.fontSize;
                  _subtitleColor = SubtitleSettingsService.color;
                  _subtitleHasBackground =
                      SubtitleSettingsService.hasBackground;
                  _subtitleFontWeight = SubtitleSettingsService.fontWeight;
                  _subtitleFontFamily = SubtitleSettingsService.fontFamily;
                  _subtitleBottomPadding =
                      SubtitleSettingsService.bottomPadding;
                  _subtitleBackgroundOpacity =
                      SubtitleSettingsService.backgroundOpacity;
                });
              },
              icon: const Icon(Icons.restart_alt),
              label: const Text('Reset subtitles'),
            ),
          ),
        ],
      ),
    );
  }
}
