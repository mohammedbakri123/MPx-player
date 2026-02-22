import '../../../library/domain/entities/video_file.dart';

class WatchHistoryEntry {
  final String videoId;
  final int positionMs;
  final int durationMs;
  final DateTime lastPlayedAt;
  final int completionPercent;
  final VideoFile? video;

  WatchHistoryEntry({
    required this.videoId,
    required this.positionMs,
    required this.durationMs,
    required this.lastPlayedAt,
    required this.completionPercent,
    this.video,
  });

  Duration get position => Duration(milliseconds: positionMs);
  Duration get duration => Duration(milliseconds: durationMs);

  double get progressFraction => durationMs > 0 ? positionMs / durationMs : 0;

  bool get isCompleted => completionPercent >= 95;
  bool get isInProgress => completionPercent > 0 && completionPercent < 95;
  bool get shouldResume => isInProgress && position.inSeconds > 5;

  WatchHistoryEntry copyWith({
    String? videoId,
    int? positionMs,
    int? durationMs,
    DateTime? lastPlayedAt,
    int? completionPercent,
    VideoFile? video,
  }) {
    return WatchHistoryEntry(
      videoId: videoId ?? this.videoId,
      positionMs: positionMs ?? this.positionMs,
      durationMs: durationMs ?? this.durationMs,
      lastPlayedAt: lastPlayedAt ?? this.lastPlayedAt,
      completionPercent: completionPercent ?? this.completionPercent,
      video: video ?? this.video,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'video_id': videoId,
      'position_ms': positionMs,
      'duration_ms': durationMs,
      'last_played_at': lastPlayedAt.millisecondsSinceEpoch,
      'completion_percent': completionPercent,
    };
  }

  factory WatchHistoryEntry.fromJson(Map<String, dynamic> json) {
    return WatchHistoryEntry(
      videoId: json['video_id'] as String,
      positionMs: json['position_ms'] as int,
      durationMs: json['duration_ms'] as int,
      lastPlayedAt:
          DateTime.fromMillisecondsSinceEpoch(json['last_played_at'] as int),
      completionPercent: json['completion_percent'] as int,
    );
  }

  String get formattedLastPlayed {
    final now = DateTime.now();
    final diff = now.difference(lastPlayedAt);

    if (diff.inMinutes < 1) return 'Just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes} min ago';
    if (diff.inHours < 24) return '${diff.inHours} hours ago';
    if (diff.inDays == 1) return 'Yesterday';
    if (diff.inDays < 7) return '${diff.inDays} days ago';
    if (diff.inDays < 30) return '${(diff.inDays / 7).floor()} weeks ago';
    if (diff.inDays < 365) return '${(diff.inDays / 30).floor()} months ago';
    return '${(diff.inDays / 365).floor()} years ago';
  }

  String get formattedProgress {
    final posMinutes = position.inMinutes;
    final posSeconds = position.inSeconds.remainder(60);
    final durMinutes = duration.inMinutes;
    final durSeconds = duration.inSeconds.remainder(60);

    return '${posMinutes.toString().padLeft(2, '0')}:${posSeconds.toString().padLeft(2, '0')} / '
        '${durMinutes.toString().padLeft(2, '0')}:${durSeconds.toString().padLeft(2, '0')}';
  }
}
