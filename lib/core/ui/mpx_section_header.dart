import 'package:flutter/material.dart';

import '../theme/app_theme_tokens.dart';
import '../theme/mpx_ui_tokens.dart';

/// Shared "FOLDERS / FILES" section header: uppercase tracked label
/// on the left, a count pill on the right. Pure presentation.
class MpxSectionHeader extends StatelessWidget {
  const MpxSectionHeader({
    super.key,
    required this.title,
    required this.count,
  });

  final String title;
  final int count;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        MpxSpacing.page,
        MpxSpacing.md,
        MpxSpacing.page,
        MpxSpacing.sm,
      ),
      child: Row(
        children: <Widget>[
          Text(
            title,
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.8,
              color: theme.mutedText,
            ),
          ),
          const SizedBox(width: MpxSpacing.sm),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Text(
              '$count',
              style: TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                color: scheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
