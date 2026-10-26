import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../models/file_item.dart';

class FileDetailsSheet extends StatelessWidget {
  const FileDetailsSheet({
    super.key,
    required this.file,
    this.onPlay,
    this.onDelete,
  });

  final FileItem file;
  final VoidCallback? onPlay;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;
    final bool isAudio = file.isAudio;

    final Color accentColor = isAudio
        ? (file.extension == 'flac' || file.extension == 'wav'
            ? theme.accentEmerald
            : theme.accentIndigo)
        : theme.accentRose;

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 12,
          bottom: 20 + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            // Grab handle
            Center(
              child: Container(
                width: 36,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: theme.faintText.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            // Header with artwork & title
            Row(
              children: <Widget>[
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: isAudio
                          ? <Color>[const Color(0xFF4F46E5), const Color(0xFF7C3AED)]
                          : <Color>[const Color(0xFFE11D48), const Color(0xFFF43F5E)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(14),
                    boxShadow: <BoxShadow>[
                      BoxShadow(
                        color: accentColor.withValues(alpha: 0.3),
                        blurRadius: 12,
                        offset: const Offset(0, 4),
                      ),
                    ],
                  ),
                  child: Icon(
                    isAudio ? Icons.music_note_rounded : Icons.videocam_rounded,
                    color: Colors.white,
                    size: 28,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        file.name,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          letterSpacing: -0.2,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: <Widget>[
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: accentColor.withValues(alpha: 0.15),
                              borderRadius: BorderRadius.circular(5),
                              border: Border.all(color: accentColor.withValues(alpha: 0.3)),
                            ),
                            child: Text(
                              file.extension.toUpperCase(),
                              style: TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.w700,
                                color: accentColor,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            file.kind.name.toUpperCase(),
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: theme.mutedText,
                              letterSpacing: 0.5,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),
            // Primary Play Action Button
            if (onPlay != null)
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  icon: const Icon(Icons.play_arrow_rounded, size: 22),
                  label: const Text(
                    'Play Media',
                    style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  ),
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                  onPressed: () {
                    Navigator.of(context).pop();
                    onPlay?.call();
                  },
                ),
              ),
            const SizedBox(height: 16),
            // Technical specs card
            Container(
              decoration: BoxDecoration(
                color: theme.subtleSurface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: scheme.outlineVariant.withValues(alpha: theme.isDarkMode ? 0.3 : 0.6),
                ),
              ),
              child: Column(
                children: <Widget>[
                  _buildSpecRow(
                    context,
                    label: 'Duration',
                    value: file.formattedDuration,
                    icon: Icons.timer_outlined,
                  ),
                  _buildDivider(scheme),
                  _buildSpecRow(
                    context,
                    label: 'File Size',
                    value: file.formattedSize,
                    icon: Icons.data_usage_rounded,
                  ),
                  _buildDivider(scheme),
                  _buildSpecRow(
                    context,
                    label: 'Virtual Path',
                    value: file.path,
                    icon: Icons.folder_open_rounded,
                    canCopy: true,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            // Delete Action
            if (onDelete != null)
              SizedBox(
                width: double.infinity,
                child: TextButton.icon(
                  icon: const Icon(Icons.delete_outline_rounded, color: Colors.redAccent, size: 18),
                  label: const Text(
                    'Delete from Library',
                    style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.w600),
                  ),
                  style: TextButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                  onPressed: () {
                    Navigator.of(context).pop();
                    onDelete?.call();
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildDivider(ColorScheme scheme) {
    return Divider(
      height: 1,
      thickness: 1,
      color: scheme.outlineVariant.withValues(alpha: 0.3),
    );
  }

  Widget _buildSpecRow(
    BuildContext context, {
    required String label,
    required String value,
    required IconData icon,
    bool canCopy = false,
  }) {
    final ThemeData theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
      child: Row(
        children: <Widget>[
          Icon(icon, size: 18, color: theme.faintText),
          const SizedBox(width: 10),
          Text(
            label,
            style: TextStyle(
              fontSize: 13,
              color: theme.mutedText,
              fontWeight: FontWeight.w500,
            ),
          ),
          const Spacer(),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: theme.strongText,
              ),
            ),
          ),
          if (canCopy) ...<Widget>[
            const SizedBox(width: 6),
            IconButton(
              icon: const Icon(Icons.copy_rounded, size: 15),
              tooltip: 'Copy path',
              constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
              padding: EdgeInsets.zero,
              onPressed: () {
                Clipboard.setData(ClipboardData(text: value));
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Path copied to clipboard'),
                    duration: Duration(seconds: 1),
                  ),
                );
              },
            ),
          ],
        ],
      ),
    );
  }
}
