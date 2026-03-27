import '../../domain/entities/video_info.dart';

class VideoThumbnailModel extends VideoThumbnail {
  const VideoThumbnailModel({
    required super.url,
    super.width,
    super.height,
  });

  factory VideoThumbnailModel.fromJson(Map<String, dynamic> json) {
    return VideoThumbnailModel(
      url: json['url'] as String? ?? '',
      width: (json['width'] as num?)?.toInt(),
      height: (json['height'] as num?)?.toInt(),
    );
  }
}

class VideoFormatModel extends VideoFormat {
  const VideoFormatModel({
    required super.id,
    super.extension,
    super.height,
    super.width,
    super.fps,
    super.fileSize,
    super.formatNote,
  });

  factory VideoFormatModel.fromJson(Map<String, dynamic> json) {
    return VideoFormatModel(
      id: json['format_id'] as String? ?? '',
      extension: json['ext'] as String?,
      height: (json['height'] as num?)?.toInt(),
      width: (json['width'] as num?)?.toInt(),
      fps: (json['fps'] as num?)?.toDouble(),
      fileSize: (json['filesize'] as num?)?.toInt(),
      formatNote: json['format_note'] as String?,
    );
  }
}

class VideoInfoModel extends VideoInfo {
  const VideoInfoModel({
    required super.id,
    required super.title,
    super.uploader,
    super.duration,
    super.webpageUrl,
    super.viewCount,
    super.thumbnails,
    super.formats,
  });

  factory VideoInfoModel.fromJson(Map<String, dynamic> json) {
    final thumbnails = (json['thumbnails'] as List<dynamic>? ?? [])
        .whereType<Map<String, dynamic>>()
        .map(VideoThumbnailModel.fromJson)
        .toList(growable: false);
    final formats = (json['formats'] as List<dynamic>? ?? [])
        .whereType<Map<String, dynamic>>()
        .map(VideoFormatModel.fromJson)
        .toList(growable: false);

    return VideoInfoModel(
      id: json['id'] as String? ?? '',
      title: json['title'] as String? ?? 'Untitled',
      uploader: json['uploader'] as String?,
      duration: (json['duration'] as num?)?.toInt(),
      webpageUrl: json['webpage_url'] as String?,
      viewCount: (json['view_count'] as num?)?.toInt(),
      thumbnails: thumbnails,
      formats: formats,
    );
  }
}
