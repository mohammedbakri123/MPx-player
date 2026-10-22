import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:photo_manager/photo_manager.dart';

import '../../../../core/utils/logger.dart';
import '../../domain/entities/video_file.dart';

class MediaLibraryDatasource {
  static const String _tag = 'MediaLibraryDatasource';

  static const PermissionRequestOption _requestOption =
      PermissionRequestOption(
    androidPermission: AndroidPermission(
      type: RequestType.video,
      mediaLocation: false,
    ),
    iosAccessLevel: IosAccessLevel.addOnly,
  );

  Future<bool> hasAccess() async {
    final PermissionState state = await PhotoManager.getPermissionState(
      requestOption: _requestOption,
    );
    return state.hasAccess;
  }

  Future<bool> requestAccess() async {
    final PermissionState state = await PhotoManager.requestPermissionExtend(
      requestOption: _requestOption,
    );
    return state.hasAccess;
  }

  Future<List<VideoFile>> fetchVideos() async {
    final List<AssetPathEntity> pathEntities =
        await PhotoManager.getAssetPathList(type: RequestType.video);
    final List<VideoFile> videos = <VideoFile>[];

    for (final AssetPathEntity pathEntity in pathEntities) {
      if (pathEntity.isAll) continue;

      final int count = await pathEntity.assetCountAsync;
      if (count <= 0) continue;

      final List<AssetEntity> assets = await pathEntity.getAssetListRange(
        start: 0,
        end: count,
      );

      for (final AssetEntity asset in assets) {
        final VideoFile? video = await _toVideoFile(asset);
        if (video != null) {
          videos.add(video);
        }
      }
    }
    return videos;
  }

  Future<VideoFile?> _toVideoFile(AssetEntity asset) async {
    try {
      final File? file = await asset.originFile;
      if (file == null) {
        return null;
      }
      final String path = file.path;
      return VideoFile(
        id: asset.id,
        path: path,
        name: asset.title ?? p.basename(path),
        durationMs: asset.duration * 1000,
        sizeBytes: await file.length(),
        dateModifiedMs: asset.modifiedDateTime.millisecondsSinceEpoch,
        folderPath: p.dirname(path),
      );
    } catch (e, s) {
      AppLogger.e(_tag, 'Failed to resolve asset ${asset.id}', e, s);
      return null;
    }
  }
}
