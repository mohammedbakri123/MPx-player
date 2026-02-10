import 'package:flutter/material.dart';
import '../../domain/entities/video_folder.dart';
import '../../controller/library_controller.dart';
import 'folder_list_card.dart';
import 'folder_grid_card.dart';
import 'home_error_state.dart';
import 'home_empty_state.dart';

class HomeContent extends StatelessWidget {
  final LibraryController controller;
  final void Function(VideoFolder folder) onFolderTap;

  const HomeContent({
    super.key,
    required this.controller,
    required this.onFolderTap,
  });

  @override
  Widget build(BuildContext context) {
    // Loading state
    if (controller.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    // Error state
    if (controller.hasError) {
      return HomeErrorState(
        errorMessage: controller.errorMessage,
        onRetry: controller.refresh,
      );
    }

    // Empty state
    if (controller.isEmpty) {
      return HomeEmptyState(
        onTryDemo: controller.loadDemoData,
      );
    }

    // Content (list or grid)
    return RefreshIndicator(
      onRefresh: controller.refresh,
      child: controller.isGridView
          ? _buildGridView(controller.folders)
          : _buildListView(controller.folders),
    );
  }

  Widget _buildListView(List<VideoFolder> folders) {
    return ListView.builder(
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
    return GridView.builder(
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
