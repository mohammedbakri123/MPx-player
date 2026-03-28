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

    return Container(
      decoration: BoxDecoration(
        color: theme.elevatedSurface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: theme.softBorder,
          width: 1.5,
        ),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButtonFormField<QualityPreference>(
          initialValue: value,
          icon: Container(
            margin: const EdgeInsets.only(right: 12),
            padding: const EdgeInsets.all(4),
            decoration: BoxDecoration(
              color: theme.colorScheme.primaryContainer,
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.keyboard_arrow_down_rounded,
              color: theme.colorScheme.primary,
              size: 18,
            ),
          ),
          style: theme.textTheme.bodyLarge?.copyWith(
            fontWeight: FontWeight.w600,
          ),
          dropdownColor: theme.elevatedSurface,
          decoration: InputDecoration(
            labelText: 'Download Quality',
            labelStyle: TextStyle(
              color: theme.colorScheme.primary,
              fontWeight: FontWeight.w600,
              fontSize: 14,
            ),
            prefixIcon: Container(
              margin: const EdgeInsets.only(left: 12, right: 8),
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
            border: InputBorder.none,
            enabledBorder: InputBorder.none,
            focusedBorder: InputBorder.none,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 8,
              vertical: 16,
            ),
          ),
          borderRadius: BorderRadius.circular(16),
          items: QualityPreference.values
              .map(
                (quality) => DropdownMenuItem<QualityPreference>(
                  value: quality,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    child: Row(
                      children: [
                        Icon(
                          _getQualityIcon(quality),
                          size: 20,
                          color: theme.mutedText,
                        ),
                        const SizedBox(width: 12),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              quality.label,
                              style: theme.textTheme.bodyLarge?.copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Text(
                              _getQualityDescription(quality),
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.faintText,
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              )
              .toList(growable: false),
          onChanged: onChanged,
        ),
      ),
    );
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
