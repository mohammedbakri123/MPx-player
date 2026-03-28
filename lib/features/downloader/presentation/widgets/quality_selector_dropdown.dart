import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

import '../../domain/enums/quality_preference.dart';

class QualitySelectorDropdown extends StatelessWidget {
  const QualitySelectorDropdown({
    super.key,
    required this.value,
    required this.onChanged,
  });

  final QualityPreference value;
  final ValueChanged<QualityPreference?> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: () => _showPicker(context),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: theme.elevatedSurface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: theme.softBorder,
            width: 1,
          ),
        ),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color:
                    theme.colorScheme.primaryContainer.withValues(alpha: 0.5),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                Icons.high_quality_rounded,
                color: theme.colorScheme.primary,
                size: 20,
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    'Download Quality',
                    style: TextStyle(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                  ),
                  Text(
                    '${value.label} — ${_getQualityDescription(value)}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.faintText,
                    ),
                  ),
                ],
              ),
            ),
            Icon(
              Icons.keyboard_arrow_down_rounded,
              color: theme.colorScheme.primary,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showPicker(BuildContext context) async {
    final theme = Theme.of(context);
    final selected = await showModalBottomSheet<QualityPreference>(
      context: context,
      backgroundColor: theme.elevatedSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(height: 12),
              Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: theme.faintText,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
              const SizedBox(height: 16),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Text(
                  'Select Quality',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(height: 8),
              ...QualityPreference.values.map((quality) {
                final isSelected = quality == value;
                return ListTile(
                  leading: Icon(
                    _getQualityIcon(quality),
                    color: isSelected
                        ? theme.colorScheme.primary
                        : theme.mutedText,
                  ),
                  title: Text(
                    quality.label,
                    style: TextStyle(
                      fontWeight:
                          isSelected ? FontWeight.w700 : FontWeight.w500,
                      color: isSelected
                          ? theme.colorScheme.primary
                          : theme.colorScheme.onSurface,
                    ),
                  ),
                  subtitle: Text(
                    _getQualityDescription(quality),
                    style: TextStyle(color: theme.faintText),
                  ),
                  trailing: isSelected
                      ? Icon(Icons.check_circle,
                          color: theme.colorScheme.primary)
                      : null,
                  onTap: () => Navigator.of(ctx).pop(quality),
                );
              }),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
    if (selected != null) {
      onChanged(selected);
    }
  }

  IconData _getQualityIcon(QualityPreference quality) {
    switch (quality) {
      case QualityPreference.auto:
        return Icons.auto_awesome_rounded;
      case QualityPreference.p1080:
        return Icons.hd_rounded;
      case QualityPreference.p720:
        return Icons.video_settings_rounded;
      case QualityPreference.p480:
        return Icons.sd_rounded;
      case QualityPreference.audioOnly:
        return Icons.headphones_rounded;
    }
  }

  String _getQualityDescription(QualityPreference quality) {
    switch (quality) {
      case QualityPreference.auto:
        return 'Best available quality';
      case QualityPreference.p1080:
        return 'Full HD video';
      case QualityPreference.p720:
        return 'HD video';
      case QualityPreference.p480:
        return 'Standard quality';
      case QualityPreference.audioOnly:
        return 'Audio track only';
    }
  }
}
