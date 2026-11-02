import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../../core/ui/mpx_empty_state.dart';
import '../../../library/controller/library_controller.dart';
import '../../../library/models/file_item.dart';
import '../../../library/presentation/widgets/file_details_sheet.dart';

/// Music page, MX structure in MPx's own code.
///
/// Tabs: Tracks / Playlists / Albums / Artists / Folders.
/// Only Tracks has real data today: audio files from the
/// current folder, or audio search results when a query is
/// typed. The other tabs are honest empty shells — there is
/// no playlist, album, or artist data model yet, so nothing
/// is invented for them.
class MusicScreen extends StatefulWidget {
  const MusicScreen({super.key});

  @override
  State<MusicScreen> createState() => _MusicScreenState();
}

class _MusicScreenState extends State<MusicScreen> {
  String _query = '';

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
    final List<FileItem> allAudio = _query.trim().isEmpty
        ? controller.files.where((FileItem f) => f.isAudio).toList()
        : controller.searchResults.where((FileItem f) => f.isAudio).toList();

    return DefaultTabController(
      length: 5,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Music'),
          actions: <Widget>[
            IconButton(
              icon: const Icon(Icons.more_vert_rounded),
              tooltip: 'More options',
              onPressed: () {},
            ),
          ],
          bottom: const TabBar(
            isScrollable: true,
            tabs: <Widget>[
              Tab(text: 'Tracks'),
              Tab(text: 'Playlists'),
              Tab(text: 'Albums'),
              Tab(text: 'Artists'),
              Tab(text: 'Folders'),
            ],
          ),
        ),
        body: TabBarView(
          children: <Widget>[
            _buildTracksTab(context, controller, allAudio),
            const _SoonTab(label: 'Playlists'),
            const _SoonTab(label: 'Albums'),
            const _SoonTab(label: 'Artists'),
            _buildFoldersTab(context, controller),
          ],
        ),
      ),
    );
  }

  Widget _buildTracksTab(
    BuildContext context,
    LibraryController controller,
    List<FileItem> tracks,
  ) {
    return Column(
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: <Widget>[
              Expanded(
                child: TextField(
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search_rounded),
                    hintText: 'Search Songs...',
                    border: OutlineInputBorder(),
                  ),
                  onChanged: (String value) {
                    setState(() {
                      _query = value;
                    });
                    controller.setSearchQuery(value);
                  },
                ),
              ),
              const SizedBox(width: 12),
              OutlinedButton.icon(
                icon: const Icon(Icons.shuffle_rounded),
                label: const Text('Shuffle All'),
                onPressed: tracks.isEmpty
                    ? null
                    : () => controller.playFile(tracks.first),
              ),
            ],
          ),
        ),
        Expanded(
          child: tracks.isEmpty
              ? const MpxEmptyState(
                  icon: Icons.music_note_rounded,
                  title: 'No tracks here',
                  message:
                      'Open a folder in Local, or search for a song above.',
                )
              : ListView.builder(
                  itemCount: tracks.length,
                  itemBuilder: (BuildContext ctx, int index) {
                    final FileItem file = tracks[index];
                    final bool isPlaying = controller.activeFile?.id == file.id &&
                        controller.isPlaying;
                    return ListTile(
                      leading: Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: Theme.of(context)
                              .colorScheme
                              .surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Icon(
                          isPlaying
                              ? Icons.equalizer_rounded
                              : Icons.music_note_rounded,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                      ),
                      title: Text(
                        file.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        '${file.extension.toUpperCase()} • ${file.formattedSize} • ${file.formattedDuration}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: IconButton(
                        icon: const Icon(Icons.more_vert_rounded),
                        tooltip: 'More actions',
                        onPressed: () => _showFileDetails(context, file),
                      ),
                      onTap: () => controller.playFile(file),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _buildFoldersTab(
    BuildContext context,
    LibraryController controller,
  ) {
    if (controller.folders.isEmpty) {
      return const MpxEmptyState(
        icon: Icons.folder_rounded,
        title: 'No folders here',
        message: 'Open a folder in Local to see its subfolders.',
      );
    }
    return ListView.builder(
      itemCount: controller.folders.length,
      itemBuilder: (BuildContext ctx, int index) {
        final folder = controller.folders[index];
        return ListTile(
          leading: const Icon(Icons.folder_rounded, size: 40),
          title: Text(folder.name),
          subtitle: Text(
            '${folder.subfolderCount} folders • ${folder.fileCount} files',
          ),
          onTap: () => controller.openFolder(folder),
        );
      },
    );
  }
}

class _SoonTab extends StatelessWidget {
  const _SoonTab({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return MpxEmptyState(
      icon: Icons.music_note_rounded,
      title: '$label are not built yet',
      message: 'There is no $label data model yet, so nothing is shown here.',
    );
  }
}
