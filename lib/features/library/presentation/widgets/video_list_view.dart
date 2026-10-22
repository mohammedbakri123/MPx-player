import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controller/library_controller.dart';
import '../../domain/entities/video_file.dart';
import 'video_list_item.dart';

class VideoListView extends StatelessWidget {
  const VideoListView({super.key});

  @override
  Widget build(BuildContext context) {
    return Selector<LibraryController, List<VideoFile>>(
      selector: (BuildContext context, LibraryController controller) =>
          controller.videos,
      builder: (BuildContext context, List<VideoFile> videos, Widget? child) {
        return ListView.separated(
          itemCount: videos.length,
          separatorBuilder: (BuildContext context, int index) =>
              const Divider(height: 1),
          itemBuilder: (BuildContext context, int index) =>
              VideoListItem(video: videos[index]),
        );
      },
    );
  }
}
