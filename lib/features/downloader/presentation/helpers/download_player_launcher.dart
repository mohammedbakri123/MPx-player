import 'dart:io';

import 'package:flutter/material.dart';

import '../../../library/domain/entities/video_file.dart';
import '../../../player/presentation/screens/video_player_screen.dart';

Future<void> openDownloadedVideo(BuildContext context, String path) async {
  final file = File(path);
  final stat = await file.stat();
  final video = VideoFile(
    id: path.hashCode.toString(),
    path: path,
    title: path.split('/').last,
    folderPath: file.parent.path,
    folderName: file.parent.path.split('/').last,
    size: stat.size,
    duration: 0,
    dateAdded: stat.modified,
  );

  if (!context.mounted) {
    return;
  }

  await Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => VideoPlayerScreen(video: video),
    ),
  );
}
