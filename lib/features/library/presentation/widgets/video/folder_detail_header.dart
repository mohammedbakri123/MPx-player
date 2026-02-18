import 'package:flutter/material.dart';
import '../../../../../core/services/video_metadata_service.dart';
import '../../../domain/entities/video_folder.dart';

class FolderDetailHeader extends StatefulWidget {
  final VideoFolder folder;
  final VoidCallback onBackPressed;

  const FolderDetailHeader({
    super.key,
    required this.folder,
    required this.onBackPressed,
  });

  @override
  State<FolderDetailHeader> createState() => _FolderDetailHeaderState();
}

class _FolderDetailHeaderState extends State<FolderDetailHeader> {
  String _durationText = '';
  bool _isLoadingDuration = true;

  @override
  void initState() {
    super.initState();
    _loadDuration();
  }

  Future<void> _loadDuration() async {
    // Check if we need to extract durations
    final videosWithZeroDuration =
        widget.folder.videos.where((v) => v.duration == 0).toList();

    if (videosWithZeroDuration.isEmpty) {
      // All videos have duration
      setState(() {
        _durationText = widget.folder.formattedDuration;
        _isLoadingDuration = false;
      });
      return;
    }

    // Extract durations for videos that don't have them
    int totalDuration = widget.folder.videos
        .where((v) => v.duration > 0)
        .fold(0, (sum, v) => sum + v.duration);

    // Extract missing durations
    for (final video in videosWithZeroDuration) {
      try {
        final metadata =
            await VideoMetadataService().extractMetadata(video.path);
        if (metadata?.duration != null) {
          totalDuration += metadata!.duration!.inMilliseconds;
        }
      } catch (e) {
        // Ignore errors for individual videos
      }
    }

    if (mounted) {
      setState(() {
        _durationText = _formatDuration(totalDuration);
        _isLoadingDuration = false;
      });
    }
  }

  String _formatDuration(int durationMs) {
    if (durationMs == 0) return '0s';

    final hours = durationMs ~/ 3600000;
    final minutes = (durationMs % 3600000) ~/ 60000;
    final seconds = (durationMs % 60000) ~/ 1000;

    if (hours > 0) {
      return '${hours}h ${minutes.toString().padLeft(2, '0')}m';
    } else if (minutes > 0) {
      return '${minutes}m ${seconds.toString().padLeft(2, '0')}s';
    } else {
      return '${seconds}s';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Row(
        children: [
          IconButton(
            onPressed: widget.onBackPressed,
            icon: const Icon(Icons.arrow_back),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.folder.name,
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    letterSpacing: -0.5,
                  ),
                ),
                Row(
                  children: [
                    Text(
                      '${widget.folder.videoCount} videos • ${widget.folder.formattedSize}',
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.grey.shade600,
                      ),
                    ),
                    if (_isLoadingDuration)
                      Padding(
                        padding: const EdgeInsets.only(left: 4),
                        child: SizedBox(
                          width: 12,
                          height: 12,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.grey.shade400,
                          ),
                        ),
                      )
                    else
                      Text(
                        ' • $_durationText',
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.grey.shade600,
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
