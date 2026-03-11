import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class FavoritesTitle extends StatelessWidget {
  final int videoCount;

  const FavoritesTitle({super.key, required this.videoCount});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Favorites',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            letterSpacing: -0.5,
            color: theme.strongText,
          ),
        ),
        Text(
          '$videoCount videos found',
          style: TextStyle(fontSize: 14, color: theme.mutedText),
        ),
      ],
    );
  }
}
