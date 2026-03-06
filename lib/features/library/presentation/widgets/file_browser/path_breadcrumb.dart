import 'package:flutter/material.dart';

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
    final segments = _segments;

    return Container(
      height: 40,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (int i = 0; i < segments.length; i++) ...[
              InkWell(
                onTap: () => onPathTap(segments[i].path),
                borderRadius: BorderRadius.circular(4),
                child: Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                  child: Text(
                    segments[i].name,
                    style: TextStyle(
                      fontSize: 13,
                      color: i == segments.length - 1
                          ? const Color(0xFF6366F1)
                          : Colors.grey[600],
                      fontWeight: i == segments.length - 1
                          ? FontWeight.w600
                          : FontWeight.normal,
                    ),
                  ),
                ),
              ),
              if (i < segments.length - 1)
                Icon(
                  Icons.chevron_right,
                  size: 18,
                  color: Colors.grey[400],
                ),
            ],
          ],
        ),
      ),
    );
  }
}

class _PathSegment {
  final String name;
  final String path;

  _PathSegment(this.name, this.path);
}
