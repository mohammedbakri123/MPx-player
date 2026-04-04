import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';
import 'package:mpx/features/settings/presentation/helpers/subtitle_font_helpers.dart';
import '../../controller/player_controller.dart';
import '../../domain/repositories/player_repository.dart';
import 'helpers/bottom_sheet_handle.dart';

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
  bool _isLoading = false;

  String _trackLabel(SubtitleTrackInfo track, int index) {
    final title = track.title?.trim();
    final language = track.language?.trim();

    if (title != null && title.isNotEmpty) return title;
    if (language != null && language.isNotEmpty) return language.toUpperCase();
    return 'Subtitle ${index + 1}';
  }

  String _weightLabel(FontWeight weight) {
    if (weight == FontWeight.w400) return 'Regular';
    if (weight == FontWeight.w600) return 'Semi-bold';
    if (weight == FontWeight.w700) return 'Bold';
    return 'Medium';
  }

  Future<void> _loadExternalSubtitle(BuildContext context) async {
    setState(() => _isLoading = true);
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: [
          'srt',
          'ass',
          'ssa',
          'sub',
          'vtt',
          'idx',
          'smi',
          'ttml',
          'dfxp',
        ],
      );
      if (result != null && result.files.single.path != null) {
        await widget.controller.loadExternalSubtitle(result.files.single.path!);
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Subtitle loaded: ${result.files.single.name}'),
              duration: const Duration(seconds: 2),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to load subtitle: $e'),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 3),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final ctrl = widget.controller;

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.88,
      ),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: SafeArea(
        top: false,
        child: AnimatedBuilder(
          animation: ctrl,
          builder: (context, _) {
            final tracks = ctrl.subtitleTracks;

            return SingleChildScrollView(
              padding: const EdgeInsets.only(bottom: 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Center(child: BottomSheetHandle()),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 6),
                    child: Text(
                      'Subtitle Settings',
                      style: TextStyle(
                        color: theme.strongText,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: Text(
                      'Choose a subtitle track and tune how captions look on screen.',
                      style: TextStyle(color: theme.mutedText, fontSize: 12),
                    ),
                  ),
                  _Section(
                    title: 'Track',
                    child: Column(
                      children: [
                        SwitchListTile.adaptive(
                          value: ctrl.subtitlesEnabled,
                          onChanged: ctrl.toggleSubtitles,
                          activeThumbColor: theme.colorScheme.primary,
                          activeTrackColor:
                              theme.colorScheme.primary.withValues(alpha: 0.32),
                          title: Text(
                            'Enable subtitles',
                            style: TextStyle(color: theme.strongText),
                          ),
                          subtitle: Text(
                            tracks.isEmpty
                                ? 'No subtitle tracks found for this video'
                                : '${tracks.length} subtitle track${tracks.length == 1 ? '' : 's'} available',
                            style: TextStyle(
                              color: theme.mutedText,
                              fontSize: 12,
                            ),
                          ),
                        ),
                        if (tracks.isNotEmpty)
                          ListTile(
                            leading: Icon(
                              Icons.closed_caption_outlined,
                              color: theme.strongText,
                            ),
                            title: Text(
                              'Subtitle track',
                              style: TextStyle(color: theme.strongText),
                            ),
                            subtitle: Text(
                              'Pick the subtitle stream for this video',
                              style: TextStyle(
                                color: theme.mutedText,
                                fontSize: 12,
                              ),
                            ),
                            trailing: _ValueChip(
                              label: ctrl.currentSubtitleTrackIndex >= 0 &&
                                      ctrl.currentSubtitleTrackIndex <
                                          tracks.length
                                  ? _trackLabel(
                                      tracks[ctrl.currentSubtitleTrackIndex],
                                      ctrl.currentSubtitleTrackIndex,
                                    )
                                  : 'Auto',
                            ),
                            onTap: () {
                              showModalBottomSheet(
                                context: context,
                                backgroundColor: Colors.transparent,
                                builder: (ctx) => _SubtitleTrackSheet(
                                  tracks: tracks,
                                  currentIndex: ctrl.currentSubtitleTrackIndex,
                                  labelBuilder: _trackLabel,
                                  onSelected: (index) {
                                    ctrl.setSubtitleTrack(index);
                                    Navigator.pop(ctx);
                                  },
                                ),
                              );
                            },
                          ),
                        ListTile(
                          leading: _isLoading
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child:
                                      CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Icon(
                                  Icons.add_circle_outline,
                                  color: Colors.white,
                                ),
                          title: Text(
                            'Load external subtitle',
                            style: TextStyle(color: theme.strongText),
                          ),
                          subtitle: Text(
                            'Pick a .srt, .ass, or other subtitle file',
                            style: TextStyle(
                              color: theme.mutedText,
                              fontSize: 12,
                            ),
                          ),
                          onTap: _isLoading
                              ? null
                              : () => _loadExternalSubtitle(context),
                        ),
                      ],
                    ),
                  ),
                  if (ctrl.subtitlesEnabled) ...[
                    _Section(
                      title: 'Appearance',
                      child: Column(
                        children: [
                          _SliderTile(
                            title: 'Font size',
                            value: ctrl.subtitleFontSize,
                            min: 16,
                            max: 72,
                            label: '${ctrl.subtitleFontSize.round()} pt',
                            onChanged: ctrl.setSubtitleFontSize,
                          ),
                          ListTile(
                            leading: Icon(
                              Icons.font_download_outlined,
                              color: theme.strongText,
                            ),
                            title: Text(
                              'Font type',
                              style: TextStyle(color: theme.strongText),
                            ),
                            subtitle: Text(
                              ctrl.subtitleFontFamily,
                              style: TextStyle(
                                color: theme.mutedText,
                                fontSize: 12,
                              ),
                            ),
                            trailing: _ValueChip(
                              label: ctrl.subtitleFontFamily,
                            ),
                            onTap: () {
                              showModalBottomSheet(
                                context: context,
                                backgroundColor: Colors.transparent,
                                builder: (ctx) => _SubtitleFontFamilySheet(
                                  currentFamily: ctrl.subtitleFontFamily,
                                  onSelected: (family) {
                                    ctrl.setSubtitleFontFamily(family);
                                    Navigator.pop(ctx);
                                  },
                                ),
                              );
                            },
                          ),
                          ListTile(
                            leading: Icon(Icons.format_bold,
                                color: theme.strongText),
                            title: Text(
                              'Font weight',
                              style: TextStyle(color: theme.strongText),
                            ),
                            subtitle: Text(
                              'Make captions lighter or heavier',
                              style: TextStyle(
                                  color: theme.mutedText, fontSize: 12),
                            ),
                            trailing: _ValueChip(
                              label: _weightLabel(ctrl.subtitleFontWeight),
                            ),
                            onTap: () {
                              showModalBottomSheet(
                                context: context,
                                backgroundColor: Colors.transparent,
                                builder: (ctx) => _SubtitleWeightSheet(
                                  currentWeight: ctrl.subtitleFontWeight,
                                  onSelected: (weight) {
                                    ctrl.setSubtitleFontWeight(weight);
                                    Navigator.pop(ctx);
                                  },
                                ),
                              );
                            },
                          ),
                          _ColorTile(controller: ctrl),
                          SwitchListTile.adaptive(
                            value: ctrl.subtitleHasBackground,
                            onChanged: ctrl.setSubtitleBackground,
                            activeThumbColor: theme.colorScheme.primary,
                            activeTrackColor: theme.colorScheme.primary
                                .withValues(alpha: 0.32),
                            title: Text(
                              'Background box',
                              style: TextStyle(color: theme.strongText),
                            ),
                            subtitle: Text(
                              'Add a dark plate behind subtitle text',
                              style: TextStyle(
                                  color: theme.mutedText, fontSize: 12),
                            ),
                          ),
                          if (ctrl.subtitleHasBackground)
                            _SliderTile(
                              title: 'Background opacity',
                              value: ctrl.subtitleBackgroundOpacity,
                              min: 0.2,
                              max: 1.0,
                              label:
                                  '${(ctrl.subtitleBackgroundOpacity * 100).round()}%',
                              onChanged: ctrl.setSubtitleBackgroundOpacity,
                            ),
                          _SliderTile(
                            title: 'Bottom spacing',
                            value: ctrl.subtitleBottomPadding,
                            min: 12,
                            max: 80,
                            label: '${ctrl.subtitleBottomPadding.round()} px',
                            onChanged: ctrl.setSubtitleBottomPadding,
                          ),
                        ],
                      ),
                    ),
                    _PreviewCard(controller: ctrl),
                  ],
                  const SizedBox(height: 20),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  final String title;
  final Widget child;

  const _Section({required this.title, required this.child});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
      child: Container(
        decoration: BoxDecoration(
          color: theme.subtleSurface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: theme.softBorder),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
              child: Text(
                title,
                style: TextStyle(
                  color: theme.strongText,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            child,
          ],
        ),
      ),
    );
  }
}

class _SliderTile extends StatelessWidget {
  final String title;
  final double value;
  final double min;
  final double max;
  final String label;
  final ValueChanged<double> onChanged;

  const _SliderTile({
    required this.title,
    required this.value,
    required this.min,
    required this.max,
    required this.label,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                title,
                style: TextStyle(
                  color: theme.strongText,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const Spacer(),
              Text(
                label,
                style: TextStyle(
                  color: theme.mutedText,
                  fontSize: 12,
                ),
              ),
            ],
          ),
          Slider(
            value: value.clamp(min, max),
            min: min,
            max: max,
            activeColor: theme.colorScheme.primary,
            inactiveColor: theme.softBorder,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
}

class _ColorTile extends StatelessWidget {
  final PlayerController controller;

  const _ColorTile({required this.controller});

  static const List<Color> _colors = [
    Colors.white,
    Color(0xFFFFEB3B),
    Color(0xFF00E5FF),
    Color(0xFF69F0AE),
    Color(0xFFFF8A80),
    Color(0xFFFFAB40),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Color',
            style: TextStyle(
              color: theme.strongText,
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: _colors.map((color) {
              final selected =
                  controller.subtitleColor.toARGB32() == color.toARGB32();
              return GestureDetector(
                onTap: () => controller.setSubtitleColor(color),
                child: Container(
                  width: 34,
                  height: 34,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: selected
                          ? theme.colorScheme.primary
                          : theme.softBorder,
                      width: selected ? 3 : 1,
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}

class _PreviewCard extends StatelessWidget {
  final PlayerController controller;

  const _PreviewCard({required this.controller});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 14),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: theme.subtleSurface,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: theme.softBorder),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Preview',
              style: TextStyle(
                color: theme.mutedText,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 18),
            Align(
              alignment: Alignment.bottomCenter,
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                color: controller.subtitleHasBackground
                    ? Colors.black.withValues(
                        alpha: controller.subtitleBackgroundOpacity,
                      )
                    : Colors.transparent,
                child: Text(
                  'This is how your subtitles will look',
                  textAlign: TextAlign.center,
                  style: SubtitleFontHelpers.textStyle(
                    controller.subtitleFontFamily,
                    color: controller.subtitleColor,
                    fontSize: controller.subtitleFontSize,
                    fontWeight: controller.subtitleFontWeight,
                    shadows: controller.subtitleHasBackground
                        ? null
                        : const [
                            Shadow(
                              blurRadius: 4,
                              color: Colors.black,
                              offset: Offset(2, 2),
                            ),
                          ],
                  ),
                ),
              ),
            ),
            SizedBox(height: controller.subtitleBottomPadding * 0.35),
          ],
        ),
      ),
    );
  }
}

class _ValueChip extends StatelessWidget {
  final String label;

  const _ValueChip({required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: theme.colorScheme.primary.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: theme.colorScheme.primary,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _SubtitleTrackSheet extends StatelessWidget {
  final List<SubtitleTrackInfo> tracks;
  final int currentIndex;
  final String Function(SubtitleTrackInfo, int) labelBuilder;
  final ValueChanged<int> onSelected;

  const _SubtitleTrackSheet({
    required this.tracks,
    required this.currentIndex,
    required this.labelBuilder,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.72,
      ),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Text(
                    'Subtitle Tracks',
                    style: TextStyle(
                      color: theme.strongText,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
            Divider(color: theme.softBorder, height: 1),
            Flexible(
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: tracks.length,
                itemBuilder: (context, index) {
                  final track = tracks[index];
                  final isSelected = index == currentIndex;
                  return ListTile(
                    title: Text(
                      labelBuilder(track, index),
                      style: TextStyle(
                        color: isSelected ? theme.strongText : theme.mutedText,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                    subtitle: track.language?.trim().isNotEmpty == true
                        ? Text(
                            track.language!,
                            style: TextStyle(
                              color: theme.faintText,
                              fontSize: 12,
                            ),
                          )
                        : null,
                    trailing: isSelected
                        ? Icon(Icons.check, color: theme.colorScheme.primary)
                        : null,
                    onTap: () => onSelected(index),
                  );
                },
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

class _SubtitleWeightSheet extends StatelessWidget {
  final FontWeight currentWeight;
  final ValueChanged<FontWeight> onSelected;

  const _SubtitleWeightSheet({
    required this.currentWeight,
    required this.onSelected,
  });

  static const List<FontWeight> weights = [
    FontWeight.w400,
    FontWeight.w500,
    FontWeight.w600,
    FontWeight.w700,
  ];

  String _label(FontWeight weight) {
    if (weight == FontWeight.w400) return 'Regular';
    if (weight == FontWeight.w600) return 'Semi-bold';
    if (weight == FontWeight.w700) return 'Bold';
    return 'Medium';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.72,
      ),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Text(
                    'Font Weight',
                    style: TextStyle(
                      color: theme.strongText,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
            Divider(color: theme.softBorder, height: 1),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: weights.map((weight) {
                  final isSelected = weight == currentWeight;
                  return ListTile(
                    title: Text(
                      _label(weight),
                      style: TextStyle(
                        color: isSelected ? theme.strongText : theme.mutedText,
                        fontWeight: weight,
                      ),
                    ),
                    trailing: isSelected
                        ? Icon(Icons.check, color: theme.colorScheme.primary)
                        : null,
                    onTap: () => onSelected(weight),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}

class _SubtitleFontFamilySheet extends StatelessWidget {
  final String currentFamily;
  final ValueChanged<String> onSelected;

  const _SubtitleFontFamilySheet({
    required this.currentFamily,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.72,
      ),
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const BottomSheetHandle(),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Text(
                    'Font Type',
                    style: TextStyle(
                      color: theme.strongText,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
            Divider(color: theme.softBorder, height: 1),
            Flexible(
              child: ListView(
                shrinkWrap: true,
                children: SubtitleFontHelpers.options.map((option) {
                  final isSelected = option.family == currentFamily;
                  return ListTile(
                    title: Text(
                      option.label,
                      style: SubtitleFontHelpers.textStyle(
                        option.family,
                        color: isSelected ? theme.strongText : theme.mutedText,
                        fontSize: 16,
                        fontWeight:
                            isSelected ? FontWeight.w700 : FontWeight.w500,
                      ),
                    ),
                    trailing: isSelected
                        ? Icon(Icons.check, color: theme.colorScheme.primary)
                        : null,
                    onTap: () => onSelected(option.family),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }
}
