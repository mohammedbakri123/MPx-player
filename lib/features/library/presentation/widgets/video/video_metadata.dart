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
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    developer.log('VideoMetadata initState for: ${widget.video.path}',
        name: 'VideoMetadata');
    _loadResolution();
  }

  Future<void> _loadResolution() async {
    developer.log('Loading resolution for: ${widget.video.path}',
        name: 'VideoMetadata');

    // If video already has resolution data, use it
    final existingResolution = widget.video.resolution;
    developer.log('Existing resolution: $existingResolution',
        name: 'VideoMetadata');

    if (existingResolution != 'Unknown') {
      setState(() {
        _resolution = existingResolution;
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
      developer.log('Metadata result: ${metadata?.width}x${metadata?.height}',
          name: 'VideoMetadata');

      if (mounted && metadata != null && metadata.height != null) {
        final formatted = _formatResolution(metadata.height!);
        developer.log('Formatted resolution: $formatted',
            name: 'VideoMetadata');
        setState(() {
          _resolution = formatted;
          _isLoading = false;
        });
      } else if (mounted) {
        developer.log('Setting resolution to Unknown (no metadata)',
            name: 'VideoMetadata');
        setState(() {
          _resolution = 'Unknown';
          _isLoading = false;
        });
      }
    } catch (e, stackTrace) {
      developer.log('Error loading resolution: $e',
          name: 'VideoMetadata', error: e, stackTrace: stackTrace);
      if (mounted) {
        setState(() {
          _resolution = 'Unknown';
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
