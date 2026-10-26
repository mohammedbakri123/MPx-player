import 'package:flutter/material.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../models/folder_item.dart';

class BreadcrumbBar extends StatelessWidget {
  const BreadcrumbBar({
    super.key,
    required this.breadcrumbs,
    required this.onSelect,
    required this.onBack,
  });

  final List<FolderItem> breadcrumbs;
  final ValueChanged<FolderItem?> onSelect;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: theme.appBackgroundAlt.withValues(alpha: theme.isDarkMode ? 0.6 : 0.8),
        border: Border(
          bottom: BorderSide(
            color: scheme.outlineVariant.withValues(alpha: theme.isDarkMode ? 0.3 : 0.6),
          ),
        ),
      ),
      child: Row(
        children: <Widget>[
          if (breadcrumbs.isNotEmpty) ...<Widget>[
            IconButton(
              icon: const Icon(Icons.arrow_back_rounded, size: 20),
              tooltip: 'Go back',
              constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
              style: IconButton.styleFrom(
                backgroundColor: scheme.surfaceContainerHigh.withValues(alpha: 0.5),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
              onPressed: onBack,
            ),
            const SizedBox(width: 8),
          ],
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              physics: const BouncingScrollPhysics(),
              child: Row(
                children: <Widget>[
                  // Root Library button
                  InkWell(
                    borderRadius: BorderRadius.circular(8),
                    onTap: () => onSelect(null),
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      decoration: BoxDecoration(
                        color: breadcrumbs.isEmpty
                            ? scheme.primary.withValues(alpha: 0.15)
                            : Colors.transparent,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: <Widget>[
                          Icon(
                            Icons.home_rounded,
                            size: 16,
                            color: breadcrumbs.isEmpty ? scheme.primary : theme.mutedText,
                          ),
                          const SizedBox(width: 6),
                          Text(
                            'Library',
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: breadcrumbs.isEmpty ? FontWeight.w700 : FontWeight.w500,
                              color: breadcrumbs.isEmpty ? scheme.primary : theme.mutedText,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  for (int i = 0; i < breadcrumbs.length; i++) ...<Widget>[
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Icon(
                        Icons.chevron_right_rounded,
                        size: 16,
                        color: theme.faintText,
                      ),
                    ),
                    InkWell(
                      borderRadius: BorderRadius.circular(8),
                      onTap: () => onSelect(breadcrumbs[i]),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                        decoration: BoxDecoration(
                          color: i == breadcrumbs.length - 1
                              ? scheme.primary.withValues(alpha: 0.15)
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          breadcrumbs[i].name,
                          style: TextStyle(
                            fontSize: 13,
                            fontWeight: i == breadcrumbs.length - 1
                                ? FontWeight.w700
                                : FontWeight.w500,
                            color: i == breadcrumbs.length - 1
                                ? scheme.primary
                                : theme.mutedText,
                          ),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
