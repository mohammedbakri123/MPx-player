import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../core/theme/app_theme_tokens.dart';
import '../../../../core/theme/mpx_ui_tokens.dart';
import '../../../../core/ui/mpx_empty_state.dart';
import '../../../../core/ui/mpx_section_header.dart';
import '../../../../core/ui/mpx_status_bar.dart';
import '../../../../core/ui/mpx_skeleton.dart';
import '../../../../core/utils/time_formatter.dart';
import '../../../player/presentation/screens/player_screen.dart';
import '../../../search/presentation/screens/search_screen.dart';
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
import '../widgets/mpx_sort_dialog.dart';

/// Local / Media page, MX structure in MPx's own code.
/// Header "Media", shortcut chips, then Folders / Files.
/// Previously this was the "Library" tab; the nav now
/// calls it Local, as in MX.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, this.onMusicTap});

  final VoidCallback? onMusicTap;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  // Presentation-only view mode for the sort dialog:
  // 'all', 'files', or 'folders'. It only filters what
  // this screen renders; it changes no controller data.
  String _viewMode = 'all';

  void _showSortDialog(BuildContext context, LibraryController controller) {
    showDialog<void>(
      context: context,
      builder: (BuildContext ctx) {
        return MpxSortDialog(
          viewMode: _viewMode,
          isGridView: controller.isGridView,
          sortOption: controller.sortOption,
          sortAscending: controller.sortAscending,
          onViewMode: (String mode) {
            setState(() {
              _viewMode = mode;
            });
          },
          onLayout: (bool grid) {
            if (grid != controller.isGridView) {
              controller.toggleViewMode();
            }
          },
          onSort: (LibrarySortOption option) {
            if (option != controller.sortOption) {
              controller.setSortOption(option);
            }
          },
          onAscending: (bool ascending) {
            if (ascending != controller.sortAscending) {
              // setSortOption toggles direction when the
              // option is unchanged, so this flips it once.
              controller.setSortOption(controller.sortOption);
            }
          },
        );
      },
    );
  }
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<LibraryController>().load();
      }
    });
  }

  void _openSearch(BuildContext context) {
    Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (BuildContext context) {
          return Consumer<LibraryController>(
            builder: (BuildContext ctx, LibraryController controller, _) {
              return SearchScreen(
                query: controller.searchQuery,
                fileResults: controller.searchResults
                    .map((FileItem file) => file.name)
                    .toList(),
                onQueryChanged: (String query) =>
                    controller.setSearchQuery(query),
                onOpenResult: (String title) {
                  final Iterable<FileItem> matches = controller.searchResults
                      .where((FileItem file) => file.name == title);
                  if (matches.isNotEmpty) {
                    controller.playFile(matches.first);
                  }
                  Navigator.of(ctx).pop();
                },
                onClose: () {
                  controller.setSearchQuery('');
                  Navigator.of(ctx).pop();
                },
              );
            },
          );
        },
      ),
    );
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

  void _openPlayer(BuildContext context, LibraryController controller) {
    final FileItem? file = controller.activeFile;
    if (file == null) {
      return;
    }
    final int positionMs =
        (controller.playbackProgress * file.durationMs).round();
    final List<FileItem> allFiles = controller.files;
    final int activeIndex =
        allFiles.indexWhere((FileItem item) => item.id == file.id);
    final List<String> upNext = activeIndex >= 0
        ? allFiles
            .skip(activeIndex + 1)
            .take(3)
            .map((FileItem item) => item.name)
            .toList()
        : const <String>[];
    Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (BuildContext context) {
          return PlayerScreen(
            title: file.name,
            subtitle: '${file.extension.toUpperCase()} • ${file.formattedSize}',
            isAudio: file.isAudio,
            isPlaying: controller.isPlaying,
            progress: controller.playbackProgress,
            positionLabel: formatDuration(positionMs),
            durationLabel: file.formattedDuration,
            upNextTitles: upNext,
            onClose: () => Navigator.of(context).pop(),
            onTogglePlay: () => controller.togglePlayPause(),
            // TODO: previous / next need a queue in the controller.
            // They are no-ops until that lands, not fake navigation.
            onPrevious: () {},
            onNext: () {},
            onSeek: (double progress) => controller.seek(progress),
          );
        },
      ),
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

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: const Text('Media'),
        actions: <Widget>[
          IconButton(
            tooltip: 'New Folder',
            icon: const Icon(Icons.create_new_folder_outlined),
            onPressed: () => _showCreateFolderDialog(context),
          ),
          IconButton(
            tooltip: 'Search library',
            icon: const Icon(Icons.search_rounded),
            onPressed: () => _openSearch(context),
          ),
          IconButton(
            tooltip: controller.isGridView ? 'List View' : 'Grid View',
            icon: Icon(
              controller.isGridView ? Icons.view_list_rounded : Icons.grid_view_rounded,
            ),
            onPressed: () => controller.toggleViewMode(),
          ),
          IconButton(
            tooltip: 'Sort and view',
            icon: const Icon(Icons.sort_rounded),
            onPressed: () => _showSortDialog(context, controller),
          ),
          PopupMenuButton<String>(
            tooltip: 'More options',
            icon: const Icon(Icons.more_vert_rounded),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(MpxRadius.lg),
            ),
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
      floatingActionButton: FloatingActionButton(
        tooltip: controller.activeFile != null ? 'Resume' : 'Play first file',
        onPressed: controller.activeFile != null
            ? () => controller.togglePlayPause()
            : controller.files.isNotEmpty
                ? () => controller.playFile(controller.files.first)
                : null,
        child: Icon(
          controller.isPlaying
              ? Icons.pause_rounded
              : Icons.play_arrow_rounded,
        ),
      ),
      body: Column(
        children: <Widget>[
          _buildShortcutChips(context),
          BreadcrumbBar(
              breadcrumbs: controller.breadcrumbs,
              onSelect: (FolderItem? folder) => controller.navigateToBreadcrumb(folder),
              onBack: () => controller.navigateUp(),
            ),
          _buildFilterBar(context, controller),
          Expanded(
            child: RefreshIndicator(
              onRefresh: () => controller.refresh(),
              child: _buildBody(context, controller),
            ),
          ),
          if (controller.folders.isNotEmpty ||
              controller.files.isNotEmpty)
            MpxStatusBar(
              label:
                  '${controller.folders.length} folders • ${controller.files.length} files',
            ),
          if (controller.activeFile != null)
            MiniPlayerDock(
              file: controller.activeFile!,
              isPlaying: controller.isPlaying,
              progress: controller.playbackProgress,
              onTogglePlay: () => controller.togglePlayPause(),
              onClose: () => controller.stopPlayback(),
              onTap: () => _openPlayer(context, controller),
            ),
        ],
      ),
    );
  }

  Widget _buildShortcutChips(BuildContext context) {
    // MX shortcut row, MPx's own labels. Only Music is
    // wired (it switches to the Music tab); the rest are
    // disabled chips until their features exist.
    final List<({String label, VoidCallback? onTap})> chips =
        <({String label, VoidCallback? onTap})>[
      (label: 'Music', onTap: widget.onMusicTap),
      (label: 'File Transfer', onTap: null),
      (label: 'Status Saver', onTap: null),
      (label: 'My Playlists', onTap: null),
      (label: 'Cleaner', onTap: null),
      (label: 'Privacy', onTap: null),
    ];
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(
        horizontal: MpxSpacing.page,
        vertical: MpxSpacing.sm,
      ),
      child: Row(
        children: <Widget>[
          for (final chip in chips) ...<Widget>[
            ActionChip(
              label: Text(chip.label),
              onPressed: chip.onTap,
            ),
            const SizedBox(width: 8),
          ],
        ],
      ),
    );
  }

  Widget _buildFilterBar(BuildContext context, LibraryController controller) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme scheme = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: MpxSpacing.page,
        vertical: MpxSpacing.sm,
      ),
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
    final ColorScheme scheme = Theme.of(context).colorScheme;

    if (controller.isLoading) {
      return controller.isGridView
          ? const MpxSkeletonGrid()
          : const MpxSkeletonList();
    }

    if (controller.errorMessage != null) {
      return MpxEmptyState(
        icon: Icons.error_outline_rounded,
        title: 'Something went wrong',
        message: controller.errorMessage!,
        isError: true,
        action: FilledButton.tonal(
          onPressed: () => controller.refresh(),
          child: const Text('Retry'),
        ),
      );
    }

    final List<FolderItem> folders =
        _viewMode == 'files' ? const <FolderItem>[] : controller.folders;
    final List<FileItem> files =
        _viewMode == 'folders' ? const <FileItem>[] : controller.files;

    if (folders.isEmpty && files.isEmpty) {
      return _buildEmptyState(context);
    }

    return LayoutBuilder(
      builder: (BuildContext ctx, BoxConstraints constraints) {
        // MX-style grid: fewer, larger thumbnails, so the
        // artwork is the hero instead of a small icon.
        final int fileColumns = constraints.maxWidth > 900
            ? 4
            : constraints.maxWidth > 650
                ? 3
                : 2;

        return CustomScrollView(
          physics: const BouncingScrollPhysics(),
          slivers: <Widget>[
            // Folder header: when inside a folder, the screen
            // is that folder, so name it and count its contents.
            if (!controller.isRoot) ...<Widget>[
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(
                    MpxSpacing.page,
                    MpxSpacing.lg,
                    MpxSpacing.page,
                    MpxSpacing.sm,
                  ),
                  child: Container(
                    padding: const EdgeInsets.all(MpxSpacing.lg),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: <Color>[
                          scheme.primary,
                          scheme.secondary,
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                      borderRadius:
                          BorderRadius.circular(MpxRadius.xl),
                    ),
                    child: Row(
                      children: <Widget>[
                        const Icon(
                          Icons.folder_rounded,
                          color: Colors.white,
                          size: 28,
                        ),
                        const SizedBox(width: MpxSpacing.md),
                        Expanded(
                          child: Column(
                            crossAxisAlignment:
                                CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(
                                controller.breadcrumbs.last.name,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 20,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${folders.length} folders • ${files.length} files',
                                style: const TextStyle(
                                  color: Colors.white70,
                                  fontSize: 13,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
            // Folders section: MX-style full-width list rows,
            // not grid cards — a media list reads top to bottom.
            if (folders.isNotEmpty) ...<Widget>[
              SliverToBoxAdapter(
                child: MpxSectionHeader(
                  title: 'FOLDERS',
                  count: folders.length,
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate(
                    (BuildContext ctx, int index) {
                      final FolderItem folder = folders[index];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: FolderCard(
                          folder: folder,
                          onTap: () => controller.openFolder(folder),
                          onDelete: () => controller.deleteFolder(folder.id),
                        ),
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
                child: MpxSectionHeader(
                  title: 'FILES',
                  count: files.length,
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

  Widget _buildEmptyState(BuildContext context) {
    return MpxEmptyState(
      icon: Icons.folder_open_rounded,
      title: 'This folder is empty',
      message: 'Create a subfolder or reset seed data from options.',
      action: FilledButton.tonalIcon(
        icon: const Icon(Icons.add_rounded),
        label: const Text('Add Subfolder'),
        onPressed: () => _showCreateFolderDialog(context),
      ),
    );
  }
}
