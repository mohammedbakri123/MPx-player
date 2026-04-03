import 'package:flutter/material.dart';
import 'package:mpx/core/theme/app_theme_tokens.dart';

class CustomFolderBackButton extends StatelessWidget {
  final VoidCallback onBack;
  final ThemeData theme;

  const CustomFolderBackButton({
    super.key,
    required this.onBack,
    required this.theme,
  });

  @override
  Widget build(BuildContext context) {
    return FloatingActionButton(
      onPressed: onBack,
      mini: true,
      backgroundColor: theme.elevatedSurface.withValues(alpha: 0.7),
      child: Icon(Icons.arrow_back_rounded, color: theme.strongText),
    );
  }
}
