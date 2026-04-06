class VideoThumbnail {
  final String url;
  final int? width;
  final int? height;

  const VideoThumbnail({
    required this.url,
    this.width,
    this.height,
  });
}

class VideoFormat {
  final String id;
  final String? extension;
  final int? height;
  final int? width;
  final double? fps;
  final int? fileSize;
  final String? formatNote;

  const VideoFormat({
    required this.id,
    this.extension,
    this.height,
    this.width,
    this.fps,
    this.fileSize,
    this.formatNote,
  });
}

class VideoInfo {
  final String id;
  final String title;
  final String? uploader;
  final int? duration;
  final String? webpageUrl;
  final int? viewCount;
  final int? fileSizeApprox;
  final List<VideoThumbnail> thumbnails;
  final List<VideoFormat> formats;

  const VideoInfo({
    required this.id,
    required this.title,
    this.uploader,
    this.duration,
    this.webpageUrl,
    this.viewCount,
    this.fileSizeApprox,
    this.thumbnails = const [],
    this.formats = const [],
  });
}
