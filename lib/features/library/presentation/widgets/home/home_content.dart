import 'package:flutter/material.dart';
import '../../../domain/entities/video_folder.dart';
import '../../../controller/library_controller.dart';
import 'folder_list_card.dart';
import 'folder_grid_card.dart';
import 'home_error_state.dart';
import 'home_empty_state.dart';
// import 'home_skeleton_loader.dart';

class HomeContent extends StatelessWidget {
  final LibraryController controller;
  final void Function(VideoFolder folder) onFolderTap;
  final ScrollController? scrollController;

  const HomeContent({
    super.key,
    required this.controller,
    required this.onFolderTap,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    // Error state - only show if we have no data
    if (controller.hasError && controller.folders.isEmpty) {
      return HomeErrorState(
        errorMessage: controller.errorMessage,
        onRetry: controller.refresh,
      );
    }

    // Empty state - only show if not loading and no folders
    if (controller.isEmpty && !controller.isLoading) {
      return HomeEmptyState(
        onTryRefresh: controller.refresh,
      );
    }

    // Show skeleton loader when loading
    // if (controller.isLoading) {
    //   // Pass the actual folder count if we have cached data, otherwise use default
    //   final itemCount = controller.folders.isNotEmpty
    //       ? controller.folders.length
    //       : (controller.isGridView ? 12 : 8);
    //   return HomeSkeletonLoader(
    //     isGridView: controller.isGridView,
    //     itemCount: itemCount,
    //   );
    // }

    // Content (list or grid) - show existing folders
    // RefreshIndicator will show its own spinner during pull-to-refresh
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: controller.isGridView
          ? _buildGridView(controller.folders)
          : _buildListView(controller.folders),
    );
  }

  Widget _buildListView(List<VideoFolder> folders) {
    if (folders.isEmpty) {
      return const SizedBox.shrink();
    }
    return ListView.builder(
      controller: scrollController,
      padding: const EdgeInsets.symmetric(horizontal: 24),
      itemCount: folders.length,
      itemBuilder: (context, index) {
        final folder = folders[index];
        return FolderListCard(
          folder: folder,
          onTap: () => onFolderTap(folder),
        );
      },
    );
  }

  Widget _buildGridView(List<VideoFolder> folders) {
    if (folders.isEmpty) {
      return const SizedBox.shrink();
    }
    return GridView.builder(
      controller: scrollController,
      padding: const EdgeInsets.symmetric(horizontal: 24),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 1,
        crossAxisSpacing: 16,
        mainAxisSpacing: 16,
      ),
      itemCount: folders.length,
      itemBuilder: (context, index) {
        final folder = folders[index];
        return FolderGridCard(
          folder: folder,
          onTap: () => onFolderTap(folder),
        );
      },
    );
  }
}
