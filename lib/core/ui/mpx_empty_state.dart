import 'package:flutter/material.dart';

import '../theme/app_theme_tokens.dart';
import '../theme/mpx_ui_tokens.dart';

/// Shared empty / error / no-results state.
///
/// [action] is a slot: the widget never decides what the action does,
/// it only renders it. That keeps this component pure UI.
class MpxEmptyState extends StatelessWidget {
  const MpxEmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.action,
    this.isError = false,
  });

  final IconData icon;
  final String title;
  final String message;
  final Widget? action;
  final bool isError;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final Color iconColor =
        isError ? theme.accentRose : theme.mutedText;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(MpxSpacing.xxl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Container(
              padding: const EdgeInsets.all(MpxSpacing.xl),
              decoration: BoxDecoration(
                color: scheme.surfaceContainerHighest
                    .withValues(alpha: 0.5),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 48, color: iconColor),
            ),
            const SizedBox(height: MpxSpacing.lg),
            Text(
              title,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: theme.strongText,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              message,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                color: theme.mutedText,
              ),
            ),
            if (action != null) ...<Widget>[
              const SizedBox(height: MpxSpacing.lg),
              action!,
            ],
          ],
        ),
      ),
    );
  }
}
