import 'dart:developer' as developer;
import 'package:flutter/material.dart';
import '../../../domain/entities/video_file.dart';
import '../../../../../core/services/video_metadata_service.dart';

class VideoMetadata extends StatefulWidget {
  final VideoFile video;

  const VideoMetadata({
    super.key,
    required this.video,
  });

  @override
  State<VideoMetadata> createState() => _VideoMetadataState();
}

class _VideoMetadataState extends State<VideoMetadata> {
  String? _resolution;
  String? _duration;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    developer.log('VideoMetadata initState for: ${widget.video.path}',
        name: 'VideoMetadata');
    _loadMetadata();
  }

  Future<void> _loadMetadata() async {
    developer.log('Loading metadata for: ${widget.video.path}',
        name: 'VideoMetadata');

    // If video already has resolution data, use it
    final existingResolution = widget.video.resolution;
    developer.log('Existing resolution: $existingResolution',
        name: 'VideoMetadata');

    // Check if we need to extract metadata
    final needsResolution = existingResolution == 'Unknown';
    final needsDuration = widget.video.duration == 0;

    if (!needsResolution && !needsDuration) {
      setState(() {
        _resolution = existingResolution;
        _duration = widget.video.formattedDuration;
      });
      return;
    }

    // Otherwise, extract metadata asynchronously
    setState(() {
      _isLoading = true;
    });

    try {
      developer.log('Calling extractMetadata...', name: 'VideoMetadata');
      final metadata =
          await VideoMetadataService().extractMetadata(widget.video.path);
      developer.log(
          'Metadata result: ${metadata?.width}x${metadata?.height}, duration: ${metadata?.duration}',
          name: 'VideoMetadata');

      if (mounted) {
        if (metadata != null) {
          // Set resolution
          if (metadata.height != null) {
            final formatted = _formatResolution(metadata.height!);
            developer.log('Formatted resolution: $formatted',
                name: 'VideoMetadata');
            setState(() {
              _resolution = formatted;
            });
          } else {
            setState(() {
              _resolution = 'Unknown';
            });
          }

          // Set duration
          if (metadata.duration != null) {
            final formattedDuration = _formatDuration(metadata.duration!);
            developer.log('Formatted duration: $formattedDuration',
                name: 'VideoMetadata');
            setState(() {
              _duration = formattedDuration;
            });
          } else {
            setState(() {
              _duration = widget.video.formattedDuration;
            });
          }
        } else {
          setState(() {
            _resolution = 'Unknown';
            _duration = widget.video.formattedDuration;
          });
        }
        setState(() {
          _isLoading = false;
        });
      }
    } catch (e, stackTrace) {
      developer.log('Error loading metadata: $e',
          name: 'VideoMetadata', error: e, stackTrace: stackTrace);
      if (mounted) {
        setState(() {
          _resolution = 'Unknown';
          _duration = widget.video.formattedDuration;
          _isLoading = false;
        });
      }
    }
  }

  String _formatResolution(int height) {
    if (height >= 2160) return '4K';
    if (height >= 1080) return '1080P';
    if (height >= 720) return '720P';
    if (height >= 480) return '480P';
    return '${height}P';
  }

  String _formatDuration(Duration duration) {
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);

    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    } else {
      return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
  }

  @override
  Widget build(BuildContext context) {
    developer.log(
        'Building VideoMetadata - resolution: $_resolution, loading: $_isLoading',
        name: 'VideoMetadata');

    return Wrap(
      spacing: 8,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(
            horizontal: 6,
            vertical: 2,
          ),
          decoration: BoxDecoration(
            color: const Color(0xFF6366F1).withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(6),
          ),
          child: _isLoading
              ? const SizedBox(
                  width: 10,
                  height: 10,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Color(0xFF6366F1),
                  ),
                )
              : Text(
                  _resolution ?? widget.video.resolution,
                  style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFF6366F1),
                  ),
                ),
        ),
        Text(
          widget.video.formattedSize,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: Colors.grey.shade500,
          ),
        ),
        Text(
          '• ${_duration ?? widget.video.formattedDuration}',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: Colors.grey.shade500,
          ),
        ),
        Text(
          '• ${widget.video.formattedDate}',
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: Colors.grey.shade500,
          ),
        ),
      ],
    );
  }
}
