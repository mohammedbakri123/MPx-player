import 'package:flutter/material.dart';
import '../../../domain/entities/video_folder.dart';
import '../../../controller/library_controller.dart';
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
        onTryDemo: controller.loadDemoData,
      );
    }

    // Content (list or grid) - show existing folders even while loading
    return Stack(
      children: [
        RefreshIndicator(
          onRefresh: controller.refresh,
          child: controller.isGridView
              ? _buildGridView(controller.folders)
              : _buildListView(controller.folders),
        ),
        // Show loading overlay only when refreshing and we have existing data
        if (controller.isLoading && controller.folders.isNotEmpty)
          Positioned(
            top: 16,
            left: 0,
            right: 0,
            child: Center(
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(20),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.1),
                      blurRadius: 10,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Color(0xFF6366F1),
                      ),
                    ),
                    SizedBox(width: 12),
                    Text(
                      'Updating...',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: Color(0xFF6366F1),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        // Show full loading indicator only when we have no data
        if (controller.isLoading && controller.folders.isEmpty)
          const Center(child: CircularProgressIndicator()),
      ],
    );
  }

  Widget _buildListView(List<VideoFolder> folders) {
    if (folders.isEmpty) {
      return const SizedBox.shrink();
    }
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
    if (folders.isEmpty) {
      return const SizedBox.shrink();
    }
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
