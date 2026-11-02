import 'package:flutter/material.dart';

import '../theme/mpx_ui_tokens.dart';

/// Bottom status bar for a media list, in MPx's own code.
///
/// Mirrors the role of the status strip at the bottom of MX's
/// media list (a thin bar under the list, above any mini player):
/// it tells you at a glance how much is in the current view.
/// It carries no playback logic and no copied artwork or text.
class MpxStatusBar extends StatelessWidget {
  const MpxStatusBar({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(
        horizontal: MpxSpacing.page,
        vertical: MpxSpacing.sm,
      ),
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(color: theme.dividerColor),
        ),
      ),
      child: Text(
        label,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          fontSize: 12,
          color: theme.textTheme.bodySmall?.color,
        ),
      ),
    );
  }
}
