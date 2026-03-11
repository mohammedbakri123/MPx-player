import 'package:flutter/material.dart';
import '../../../../../core/theme/app_theme_tokens.dart';

class PathBreadcrumb extends StatelessWidget {
  final String currentPath;
  final void Function(String path) onPathTap;

  const PathBreadcrumb({
    super.key,
    required this.currentPath,
    required this.onPathTap,
  });

  List<_PathSegment> get _segments {
    if (currentPath.isEmpty) return [];

    final parts = currentPath.split('/').where((p) => p.isNotEmpty).toList();
    final segments = <_PathSegment>[];

    String buildPath = '';
    if (currentPath.startsWith('/storage')) {
      buildPath = '/storage/emulated/0';
      segments.add(_PathSegment('Internal Storage', buildPath));
      buildPath += '/';
    }

    for (int i = 0; i < parts.length; i++) {
      if (parts[i] == 'storage' && i == 0) continue;
      if (parts[i] == 'emulated' && i == 1) continue;
      if (parts[i] == '0' && i == 2) continue;

      buildPath += parts[i];
      segments.add(_PathSegment(parts[i], buildPath));
      if (i < parts.length - 1) buildPath += '/';
    }

    return segments;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final segments = _segments;

    if (segments.isEmpty) {
      return const SizedBox.shrink();
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: theme.elevatedSurface
            .withValues(alpha: theme.isDarkMode ? 0.92 : 0.9),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: theme.softBorder),
      ),
      child: Row(
        children: [
          Icon(
            Icons.route_rounded,
            size: 18,
            color: theme.mutedText,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (int i = 0; i < segments.length; i++) ...[
                    Material(
                      color: i == segments.length - 1
                          ? const Color(0xFF2563EB)
                              .withValues(alpha: theme.isDarkMode ? 0.22 : 0.14)
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(999),
                      child: InkWell(
                        onTap: () => onPathTap(segments[i].path),
                        borderRadius: BorderRadius.circular(999),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          child: Text(
                            segments[i].name,
                            style: TextStyle(
                              fontSize: 12,
                              color: i == segments.length - 1
                                  ? const Color(0xFF1D4ED8)
                                  : theme.mutedText,
                              fontWeight: i == segments.length - 1
                                  ? FontWeight.w700
                                  : FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    ),
                    if (i < segments.length - 1)
                      const Padding(
                        padding: EdgeInsets.symmetric(horizontal: 2),
                        child: Icon(
                          Icons.chevron_right_rounded,
                          size: 16,
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

class _PathSegment {
  final String name;
  final String path;

  _PathSegment(this.name, this.path);
}
