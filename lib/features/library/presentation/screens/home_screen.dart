import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../controller/library_controller.dart';
import '../../models/file_item.dart';
import '../../models/folder_item.dart';
import '../../models/library_sort_option.dart';
import '../widgets/breadcrumb_bar.dart';
import '../widgets/file_details_sheet.dart';
import '../widgets/file_grid_card.dart';
import '../widgets/file_list_item.dart';
import '../widgets/folder_card.dart';
import '../widgets/mini_player_dock.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final TextEditingController _searchController = TextEditingController();
  bool _isSearching = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<LibraryController>().load();
      }
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _showCreateFolderDialog(BuildContext context) {
    final TextEditingController nameController = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (BuildContext ctx) {
        return AlertDialog(
          title: const Text('Create New Folder'),
          content: TextField(
            controller: nameController,
            autofocus: true,
            decoration: const InputDecoration(
              labelText: 'Folder Name',
              hintText: 'e.g. Ambient Chill',
            ),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final String text = nameController.text.trim();
                if (text.isNotEmpty) {
                  context.read<LibraryController>().addFolder(text);
                  Navigator.of(ctx).pop();
                }
              },
              child: const Text('Create'),
            ),
          ],
        );
      },
    );
  }

  void _showFileDetails(BuildContext context, FileItem file) {
    final LibraryController controller = context.read<LibraryController>();
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (BuildContext ctx) {
        return FileDetailsSheet(
          file: file,
          onPlay: () => controller.playFile(file),
          onDelete: () => controller.deleteFile(file.id),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final LibraryController controller = context.watch<LibraryController>();
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: _isSearching
            ? TextField(
                controller: _searchController,
                autofocus: true,
                style: TextStyle(color: theme.strongText, fontSize: 16),
                decoration: InputDecoration(
                  hintText: 'Search files across library...',
                  filled: false,
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  hintStyle: TextStyle(color: theme.mutedText),
                ),
                onChanged: (String val) => controller.setSearchQuery(val),
              )
            : Row(
                children: <Widget>[
                  Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: <Color>[scheme.primary, scheme.secondary],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(Icons.play_arrow_rounded, color: Colors.white, size: 20),
                  ),
                  const SizedBox(width: 10),
                  const Text('MPx Player'),
                ],
              ),
        actions: <Widget>[
          IconButton(
            tooltip: _isSearching ? 'Close search' : 'Search library',
            icon: Icon(_isSearching ? Icons.close_rounded : Icons.search_rounded),
            onPressed: () {
              setState(() {
                _isSearching = !_isSearching;
                if (!_isSearching) {
                  _searchController.clear();
                  controller.setSearchQuery('');
                }
              });
            },
          ),
          IconButton(
            tooltip: controller.isGridView ? 'List View' : 'Grid View',
            icon: Icon(
              controller.isGridView ? Icons.view_list_rounded : Icons.grid_view_rounded,
            ),
            onPressed: () => controller.toggleViewMode(),
          ),
          PopupMenuButton<LibrarySortOption>(
            tooltip: 'Sort by',
            icon: const Icon(Icons.sort_rounded),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            initialValue: controller.sortOption,
            onSelected: (LibrarySortOption option) => controller.setSortOption(option),
            itemBuilder: (BuildContext ctx) => LibrarySortOption.values.map(
              (LibrarySortOption option) {
                final bool isSelected = controller.sortOption == option;
                return PopupMenuItem<LibrarySortOption>(
                  value: option,
                  child: Row(
                    children: <Widget>[
                      Icon(
                        isSelected
                            ? (controller.sortAscending
                                ? Icons.arrow_upward_rounded
                                : Icons.arrow_downward_rounded)
                            : Icons.check,
                        size: 16,
                        color: isSelected ? scheme.primary : Colors.transparent,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        option.label,
                        style: TextStyle(
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          color: isSelected ? scheme.primary : theme.strongText,
                        ),
                      ),
                    ],
                  ),
                );
              },
            ).toList(),
          ),
          IconButton(
            tooltip: 'New Folder',
            icon: const Icon(Icons.create_new_folder_outlined),
            onPressed: () => _showCreateFolderDialog(context),
          ),
          PopupMenuButton<String>(
            tooltip: 'More options',
            icon: const Icon(Icons.more_vert_rounded),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            itemBuilder: (BuildContext ctx) => <PopupMenuEntry<String>>[
              const PopupMenuItem<String>(
                value: 'refresh',
                child: Row(
                  children: <Widget>[
                    Icon(Icons.refresh_rounded, size: 20),
                    SizedBox(width: 10),
                    Text('Refresh'),
                  ],
                ),
              ),
              const PopupMenuItem<String>(
                value: 'reset_db',
                child: Row(
                  children: <Widget>[
                    Icon(Icons.restart_alt_rounded, size: 20),
                    SizedBox(width: 10),
                    Text('Reset Seed Data'),
                  ],
                ),
              ),
            ],
            onSelected: (String action) {
              if (action == 'refresh') {
                controller.refresh();
              } else if (action == 'reset_db') {
                controller.resetDummyData();
              }
            },
          ),
        ],
      ),
      body: Column(
        children: <Widget>[
          if (!_isSearching)
            BreadcrumbBar(
              breadcrumbs: controller.breadcrumbs,
              onSelect: (FolderItem? folder) => controller.navigateToBreadcrumb(folder),
              onBack: () => controller.navigateUp(),
            ),
          _buildFilterBar(context, controller),
          Expanded(
            child: _buildBody(context, controller),
          ),
          if (controller.activeFile != null)
            MiniPlayerDock(
              file: controller.activeFile!,
              isPlaying: controller.isPlaying,
              progress: controller.playbackProgress,
              onTogglePlay: () => controller.togglePlayPause(),
              onClose: () => controller.stopPlayback(),
              onTap: () => _showFileDetails(context, controller.activeFile!),
            ),
        ],
      ),
    );
  }

  Widget _buildFilterBar(BuildContext context, LibraryController controller) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: <Widget>[
          FilterChip(
            label: const Text('All Files'),
            selected: controller.filterKind == null,
            onSelected: (_) => controller.setFilterKind(null),
          ),
          const SizedBox(width: 8),
          FilterChip(
            avatar: Icon(
              Icons.music_note_rounded,
              size: 16,
              color: controller.filterKind == FileKind.audio
                  ? scheme.onPrimaryContainer
                  : theme.accentIndigo,
            ),
            label: const Text('Audio'),
            selected: controller.filterKind == FileKind.audio,
            onSelected: (_) => controller.setFilterKind(FileKind.audio),
          ),
          const SizedBox(width: 8),
          FilterChip(
            avatar: Icon(
              Icons.movie_rounded,
              size: 16,
              color: controller.filterKind == FileKind.video
                  ? scheme.onPrimaryContainer
                  : theme.accentRose,
            ),
            label: const Text('Video'),
            selected: controller.filterKind == FileKind.video,
            onSelected: (_) => controller.setFilterKind(FileKind.video),
          ),
        ],
      ),
    );
  }

  Widget _buildBody(BuildContext context, LibraryController controller) {
    if (controller.isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (controller.errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const Icon(Icons.error_outline, size: 48, color: Colors.redAccent),
              const SizedBox(height: 12),
              Text(
                controller.errorMessage!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.redAccent),
              ),
              const SizedBox(height: 16),
              FilledButton.tonal(
                onPressed: () => controller.refresh(),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_isSearching && controller.searchQuery.isNotEmpty) {
      return _buildSearchResults(context, controller);
    }

    final List<FolderItem> folders = controller.folders;
    final List<FileItem> files = controller.files;

    if (folders.isEmpty && files.isEmpty) {
      return _buildEmptyState(context);
    }

    return LayoutBuilder(
      builder: (BuildContext ctx, BoxConstraints constraints) {
        final int folderColumns = constraints.maxWidth > 900
            ? 4
            : constraints.maxWidth > 600
                ? 3
                : constraints.maxWidth > 400
                    ? 2
                    : 1;

        final int fileColumns = constraints.maxWidth > 900
            ? 5
            : constraints.maxWidth > 650
                ? 4
                : constraints.maxWidth > 450
                    ? 3
                    : 2;

        return CustomScrollView(
          physics: const BouncingScrollPhysics(),
          slivers: <Widget>[
            // Folders section
            if (folders.isNotEmpty) ...<Widget>[
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: Row(
                    children: <Widget>[
                      const Text(
                        'FOLDERS',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.8,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: Theme.of(context).colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          '${folders.length}',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                sliver: SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: folderColumns,
                    mainAxisExtent: 74,
                    mainAxisSpacing: 8,
                    crossAxisSpacing: 8,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (BuildContext ctx, int index) {
                      final FolderItem folder = folders[index];
                      return FolderCard(
                        folder: folder,
                        onTap: () => controller.openFolder(folder),
                        onDelete: () => controller.deleteFolder(folder.id),
                      );
                    },
                    childCount: folders.length,
                  ),
                ),
              ),
            ],
            // Files section
            if (files.isNotEmpty) ...<Widget>[
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 18, 16, 8),
                  child: Row(
                    children: <Widget>[
                      const Text(
                        'FILES',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 0.8,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: Theme.of(context).colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          '${files.length}',
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (controller.isGridView)
                SliverPadding(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                  sliver: SliverGrid(
                    gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: fileColumns,
                      mainAxisSpacing: 10,
                      crossAxisSpacing: 10,
                      childAspectRatio: 0.85,
                    ),
                    delegate: SliverChildBuilderDelegate(
                      (BuildContext ctx, int index) {
                        final FileItem file = files[index];
                        final bool isPlaying =
                            controller.activeFile?.id == file.id && controller.isPlaying;
                        return FileGridCard(
                          file: file,
                          isPlaying: isPlaying,
                          onTap: () => _showFileDetails(context, file),
                          onPlay: () {
                            if (isPlaying) {
                              controller.togglePlayPause();
                            } else {
                              controller.playFile(file);
                            }
                          },
                          onDelete: () => controller.deleteFile(file.id),
                        );
                      },
                      childCount: files.length,
                    ),
                  ),
                )
              else
                SliverPadding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  sliver: SliverList(
                    delegate: SliverChildBuilderDelegate(
                      (BuildContext ctx, int index) {
                        final FileItem file = files[index];
                        final bool isPlaying =
                            controller.activeFile?.id == file.id && controller.isPlaying;
                        return FileListItem(
                          file: file,
                          isPlaying: isPlaying,
                          onTap: () => _showFileDetails(context, file),
                          onPlay: () {
                            if (isPlaying) {
                              controller.togglePlayPause();
                            } else {
                              controller.playFile(file);
                            }
                          },
                          onDelete: () => controller.deleteFile(file.id),
                        );
                      },
                      childCount: files.length,
                    ),
                  ),
                ),
            ],
            const SliverToBoxAdapter(child: SizedBox(height: 28)),
          ],
        );
      },
    );
  }

  Widget _buildSearchResults(
    BuildContext context,
    LibraryController controller,
  ) {
    final List<FileItem> results = controller.searchResults;
    if (results.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(
              Icons.search_off_rounded,
              size: 48,
              color: Theme.of(context).colorScheme.outline,
            ),
            const SizedBox(height: 12),
            Text(
              'No files matching "${controller.searchQuery}"',
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      itemCount: results.length,
      itemBuilder: (BuildContext ctx, int index) {
        final FileItem file = results[index];
        final bool isPlaying =
            controller.activeFile?.id == file.id && controller.isPlaying;
        return FileListItem(
          file: file,
          isPlaying: isPlaying,
          onTap: () => _showFileDetails(context, file),
          onPlay: () {
            if (isPlaying) {
              controller.togglePlayPause();
            } else {
              controller.playFile(file);
            }
          },
          onDelete: () => controller.deleteFile(file.id),
        );
      },
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerHighest.withValues(alpha: 0.5),
              shape: BoxShape.circle,
            ),
            child: Icon(
              Icons.folder_open_rounded,
              size: 48,
              color: scheme.outline,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'This folder is empty',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: scheme.onSurface,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Create a subfolder or reset seed data from options.',
            style: TextStyle(
              fontSize: 13,
              color: scheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 16),
          FilledButton.tonalIcon(
            icon: const Icon(Icons.add_rounded),
            label: const Text('Add Subfolder'),
            onPressed: () => _showCreateFolderDialog(context),
          ),
        ],
      ),
    );
  }
}
