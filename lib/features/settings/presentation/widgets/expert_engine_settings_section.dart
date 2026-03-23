import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

import '../../controllers/app_settings_controller.dart';
import '../../services/app_settings_service.dart';
import '../helpers/settings_helpers.dart';
import 'common_widgets.dart';
import 'form_rows.dart';

class ExpertEngineSettingsSection extends StatelessWidget {
  final AppSettingsController settings;
  final Future<void> Function()? onSettingsChanged;

  const ExpertEngineSettingsSection({
    super.key,
    required this.settings,
    this.onSettingsChanged,
  });

  Future<void> _updateSettings(ExpertEngineSettings next) async {
    await settings.setExpertEngineSettings(next);
    await onSettingsChanged?.call();
  }

  Future<void> _toggleExpert(bool value) async {
    await settings.setExpertEngineEnabled(value);
    await onSettingsChanged?.call();
  }

  Future<void> _applyBasePreset(VideoPerformancePreset preset) async {
    await settings.resetExpertEngineSettingsFromPreset(preset);
    await onSettingsChanged?.call();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final engine = settings.expertEngineSettings;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SettingsSwitchRow(
          icon: Icons.memory_rounded,
          title: 'Use expert engine settings',
          subtitle:
              'When enabled, custom mpv engine values override the simple engine profile.',
          value: settings.expertEngineEnabled,
          onChanged: _toggleExpert,
        ),
        const SizedBox(height: 10),
        Text(
          settings.expertEngineEnabled
              ? 'Expert mode is active. The engine profile is ignored until expert mode is turned off.'
              : 'Simple engine profile remains active. Expert values are stored as a draft until you enable them.',
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.mutedText,
            height: 1.4,
          ),
        ),
        const SizedBox(height: 16),
        const _ExpertLabel(
          title: 'Start from a preset',
          subtitle:
              'Use a tested profile to prefill the expert fields, then fine-tune safely.',
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: VideoPerformancePreset.values
              .map(
                (preset) => SizedBox(
                  width: 170,
                  child: SettingsChoiceCard(
                    icon: SettingsVideoPerformanceHelpers.getIcon(preset),
                    title: SettingsVideoPerformanceHelpers.getLabel(preset),
                    subtitle:
                        SettingsVideoPerformanceHelpers.getDescription(preset),
                    isSelected: settings.videoPerformancePreset == preset,
                    onTap: () => _applyBasePreset(preset),
                  ),
                ),
              )
              .toList(),
        ),
        const SizedBox(height: 14),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: theme.colorScheme.primary.withValues(
              alpha: theme.isDarkMode ? 0.14 : 0.1,
            ),
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
              color: theme.colorScheme.primary.withValues(
                alpha: theme.isDarkMode ? 0.18 : 0.12,
              ),
            ),
          ),
          child: Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              SettingsInlineStat(
                label: 'Base',
                value: SettingsVideoPerformanceHelpers.getBadge(
                  settings.videoPerformancePreset,
                ),
              ),
              SettingsInlineStat(
                  label: 'Threads', value: '${engine.decoderThreads}'),
              SettingsInlineStat(label: 'Sync', value: engine.videoSync),
              SettingsInlineStat(label: 'Drop', value: engine.frameDropping),
            ],
          ),
        ),
        const SizedBox(height: 16),
        const _ExpertLabel(
          title: 'Decoder and sync',
          subtitle:
              'Control hardware decode, threads, frame dropping, and timing.',
        ),
        const SizedBox(height: 8),
        _OptionChipsRow(
          label: 'Hardware decode',
          current: engine.hardwareDecoding,
          options: const ['auto', 'auto-copy', 'yes', 'no'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(hardwareDecoding: value)),
        ),
        _OptionChipsRow(
          label: 'Frame dropping',
          current: engine.frameDropping,
          options: const ['no', 'decoder', 'decoder+vo'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(frameDropping: value)),
        ),
        _OptionChipsRow(
          label: 'Video sync',
          current: engine.videoSync,
          options: const [
            'audio',
            'display-resample',
            'display-resample-vdrop'
          ],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(videoSync: value)),
        ),
        _StepperRow(
          icon: Icons.developer_board_rounded,
          title: 'Decoder threads',
          subtitle: 'Higher values help heavy files but use more CPU.',
          value: engine.decoderThreads,
          min: 1,
          max: 8,
          onChanged: (value) =>
              _updateSettings(engine.copyWith(decoderThreads: value)),
        ),
        const SizedBox(height: 16),
        const _ExpertLabel(
          title: 'Quality and rendering',
          subtitle:
              'Tune scaling, interpolation, deinterlacing, and GPU behavior.',
        ),
        const SizedBox(height: 8),
        _OptionChipsRow(
          label: 'Scaler',
          current: engine.scaler,
          options: const ['bilinear', 'bicubic', 'lanczos'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(scaler: value)),
        ),
        _OptionChipsRow(
          label: 'Downscaler',
          current: engine.downScaler,
          options: const ['bilinear', 'bicubic', 'lanczos'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(downScaler: value)),
        ),
        _OptionChipsRow(
          label: 'Deinterlace',
          current: engine.deinterlacing,
          options: const ['auto', 'yes', 'no'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(deinterlacing: value)),
        ),
        _OptionChipsRow(
          label: 'GPU backend',
          current: engine.gpuBackend,
          options: const ['auto', 'opengl', 'vulkan'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(gpuBackend: value)),
        ),
        _OptionChipsRow(
          label: 'GPU API',
          current: engine.gpuApi,
          options: const ['auto', 'opengl', 'vulkan'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(gpuApi: value)),
        ),
        SettingsSwitchRow(
          icon: Icons.motion_photos_on_rounded,
          title: 'Interpolation',
          subtitle: 'Smooth motion but heavier GPU usage.',
          value: engine.interpolation,
          onChanged: (value) =>
              _updateSettings(engine.copyWith(interpolation: value)),
        ),
        if (engine.interpolation)
          _OptionChipsRow(
            label: 'Temporal scaler',
            current: engine.temporalScaler,
            options: const ['oversample', 'linear', 'cosine'],
            onSelected: (value) =>
                _updateSettings(engine.copyWith(temporalScaler: value)),
          ),
        const SizedBox(height: 16),
        const _ExpertLabel(
          title: 'Cache and seeking',
          subtitle:
              'Adjust buffering and seek behavior for local or online playback.',
        ),
        const SizedBox(height: 8),
        SettingsSwitchRow(
          icon: Icons.sd_storage_rounded,
          title: 'Optimize for local files',
          subtitle: 'Prefer local-file seek behavior over network buffering.',
          value: engine.optimizeForLocalFiles,
          onChanged: (value) =>
              _updateSettings(engine.copyWith(optimizeForLocalFiles: value)),
        ),
        _OptionChipsRow(
          label: 'Cache mode',
          current: engine.cache,
          options: const ['yes', 'no'],
          onSelected: (value) => _updateSettings(engine.copyWith(cache: value)),
        ),
        _StepperRow(
          icon: Icons.timelapse_rounded,
          title: 'Cache seconds',
          subtitle:
              'Larger cache helps unstable playback but uses more memory.',
          value: engine.cacheSecs,
          min: 5,
          max: 180,
          step: 5,
          onChanged: (value) =>
              _updateSettings(engine.copyWith(cacheSecs: value)),
        ),
        _OptionChipsRow(
          label: 'Cache back',
          current: engine.cacheBack,
          options: const ['64M', '128M', '256M'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(cacheBack: value)),
        ),
        _OptionChipsRow(
          label: 'Demuxer max',
          current: engine.demuxerMaxBytes,
          options: const ['32M', '64M', '128M'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(demuxerMaxBytes: value)),
        ),
        _OptionChipsRow(
          label: 'Back buffer',
          current: engine.demuxerMaxBackBytes,
          options: const ['64M', '128M', '256M'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(demuxerMaxBackBytes: value)),
        ),
        _OptionChipsRow(
          label: 'HR seek',
          current: engine.hrSeek,
          options: const ['yes', 'no'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(hrSeek: value)),
        ),
        _OptionChipsRow(
          label: 'Fast seek',
          current: engine.fastSeek,
          options: const ['yes', 'no'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(fastSeek: value)),
        ),
        _OptionChipsRow(
          label: 'Seek framedrop',
          current: engine.hrSeekFramedrop,
          options: const ['yes', 'no'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(hrSeekFramedrop: value)),
        ),
        const SizedBox(height: 16),
        const _ExpertLabel(
          title: 'Compatibility',
          subtitle: 'Use these when a device decoder is unstable or broken.',
        ),
        const SizedBox(height: 8),
        _OptionChipsRow(
          label: 'Fast decoding',
          current: engine.fastDecoding,
          options: const ['no', 'yes'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(fastDecoding: value)),
        ),
        _OptionChipsRow(
          label: 'HW codecs',
          current: engine.hwdecCodecs,
          options: const ['all', 'h264', 'h264,hevc', 'hevc'],
          onSelected: (value) =>
              _updateSettings(engine.copyWith(hwdecCodecs: value)),
        ),
      ],
    );
  }
}

class _ExpertLabel extends StatelessWidget {
  final String title;
  final String subtitle;

  const _ExpertLabel({required this.title, required this.subtitle});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: theme.textTheme.titleMedium?.copyWith(
            color: theme.strongText,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          subtitle,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.mutedText,
            height: 1.4,
          ),
        ),
      ],
    );
  }
}

class _OptionChipsRow extends StatelessWidget {
  final String label;
  final String current;
  final List<String> options;
  final ValueChanged<String> onSelected;

  const _OptionChipsRow({
    required this.label,
    required this.current,
    required this.options,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.strongText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: options
                .map(
                  (option) => ChoiceChip(
                    label: Text(option),
                    selected: current == option,
                    onSelected: (_) => onSelected(option),
                  ),
                )
                .toList(),
          ),
        ],
      ),
    );
  }
}

class _StepperRow extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final int value;
  final int min;
  final int max;
  final int step;
  final ValueChanged<int> onChanged;

  const _StepperRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.min,
    required this.max,
    this.step = 1,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.subtleSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: theme.softBorder),
      ),
      child: Row(
        children: [
          Icon(icon, color: theme.colorScheme.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: TextStyle(
                    color: theme.strongText,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  subtitle,
                  style: TextStyle(
                    color: theme.mutedText,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            onPressed: value > min ? () => onChanged(value - step) : null,
            icon: const Icon(Icons.remove_circle_outline_rounded),
          ),
          Text(
            '$value',
            style: TextStyle(
              color: theme.strongText,
              fontWeight: FontWeight.w700,
            ),
          ),
          IconButton(
            onPressed: value < max ? () => onChanged(value + step) : null,
            icon: const Icon(Icons.add_circle_outline_rounded),
          ),
        ],
      ),
    );
  }
}
