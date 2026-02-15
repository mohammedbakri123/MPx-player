import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../../core/services/play_history_service.dart';
import '../../../player/presentation/screens/video_player_screen.dart';
import '../../domain/entities/video_file.dart';
import '../../domain/entities/video_folder.dart';
import '../../controller/library_controller.dart';
import 'folder_detail_screen.dart';
import '../widgets/home/home_header.dart';
import '../widgets/home/home_section_header.dart';
import '../widgets/home/home_content.dart';
import '../widgets/home/home_fab.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  List<PlayHistoryEntry> _continueWatchingList = [];

  @override
  void initState() {
    super.initState();
    // Load videos on screen initialization
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<LibraryController>().load();
      _loadContinueWatching();
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Refresh continue watching when returning from other screens
    _loadContinueWatching();
  }

  Future<void> _loadContinueWatching() async {
    final history = await PlayHistoryService.getRecentHistory(limit: 5);
    final inProgressVideos = history.where((entry) {
      final progress = entry.progressPercent;
      return progress > 0.05 && progress < 0.95;
    }).toList();

    if (mounted) {
      setState(() {
        _continueWatchingList = inProgressVideos;
      });
    }
  }

  void _openVideoPlayer(String videoPath) {
    // Find the video from the history entry
    final entry = _continueWatchingList.cast<PlayHistoryEntry?>().firstWhere(
          (e) => e?.videoPath == videoPath,
          orElse: () => null,
        );

    if (entry == null) return;

    // Create a VideoFile from the history entry
    final videoFile = VideoFile(
      id: entry.videoId,
      path: entry.videoPath,
      title: entry.title,
      folderPath: '',
      folderName: '',
      size: 0,
      duration: entry.totalDurationMs,
      dateAdded: entry.lastWatched,
    );

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => VideoPlayerScreen(video: videoFile),
      ),
    ).then((_) {
      // Refresh continue watching list when returning
      _loadContinueWatching();
    });
  }

  @override
  Widget build(BuildContext context) {
    // Watch controller for reactive updates
    final controller = context.watch<LibraryController>();

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            // Header
            const HomeHeader(),

            // Section Header
            HomeSectionHeader(controller: controller),

            const SizedBox(height: 16),

            // Continue Watching Section
            if (_continueWatchingList.isNotEmpty) ...[
              _buildContinueWatchingSection(),
              const SizedBox(height: 16),
            ],

            // Folder List/Grid
            Expanded(
              child: HomeContent(
                controller: controller,
                onFolderTap: _openFolder,
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: const HomeFAB(),
    );
  }

  void _openFolder(VideoFolder folder) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => FolderDetailScreen(folder: folder),
      ),
    );
  }

  Widget _buildContinueWatchingSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section Header
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Row(
            children: [
              Text(
                'Continue Watching',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Colors.grey.shade500,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        // Horizontal List
        SizedBox(
          height: 160,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 24),
            itemCount: _continueWatchingList.length,
            itemBuilder: (context, index) {
              final entry = _continueWatchingList[index];
              return _buildContinueWatchingItem(entry);
            },
          ),
        ),
      ],
    );
  }

  Widget _buildContinueWatchingItem(PlayHistoryEntry entry) {
    final progress = entry.progressPercent;
    final progressPercent = (progress * 100).round();

    return GestureDetector(
      onTap: () => _openVideoPlayer(entry.videoPath),
      child: Container(
        width: 140,
        margin: const EdgeInsets.only(right: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 20,
              offset: const Offset(0, 4),
            ),
          ],
          border: Border.all(color: Colors.grey.shade100),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Thumbnail
            ClipRRect(
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(16),
                topRight: Radius.circular(16),
              ),
              child: Container(
                height: 80,
                width: double.infinity,
                color: const Color(0xFF6366F1).withValues(alpha: 0.08),
                child: const Icon(
                  Icons.play_circle_outline,
                  color: Color(0xFF6366F1),
                  size: 32,
                ),
              ),
            ),
            // Content
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Title
                    Text(
                      entry.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFF1E293B),
                      ),
                    ),
                    const SizedBox(height: 4),
                    // Progress text
                    Text(
                      'Continue • $progressPercent%',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w500,
                        color: Colors.grey.shade500,
                      ),
                    ),
                    const SizedBox(height: 6),
                    // Progress bar
                    ClipRRect(
                      borderRadius: BorderRadius.circular(2),
                      child: LinearProgressIndicator(
                        value: progress,
                        backgroundColor: Colors.grey.shade200,
                        valueColor: const AlwaysStoppedAnimation<Color>(
                          Color(0xFF6366F1),
                        ),
                        minHeight: 3,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
