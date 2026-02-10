import 'package:flutter/material.dart';
import '../../../library/domain/entities/video_file.dart';
// import '../../../library/domain/entities/video_folder.dart';
import '../../../library/data/datasources/local_video_scanner.dart';
import '../../../player/presentation/screens/video_player_screen.dart';

class FavoritesScreen extends StatefulWidget {
  const FavoritesScreen({super.key});

  @override
  State<FavoritesScreen> createState() => _FavoritesScreenState();
}

class _FavoritesScreenState extends State<FavoritesScreen> {
  List<VideoFile> _videos = [];
  bool _isLoading = true;
  bool _isScanning = false; // Prevent multiple scans
  bool _isNavigating = false; // Prevent multiple video opens

  @override
  void initState() {
    super.initState();
    _loadAllVideos();
  }

  Future<void> _loadAllVideos() async {
    // Prevent multiple simultaneous scans
    if (_isScanning) return;

    setState(() {
      _isLoading = true;
      _isScanning = true;
    });

    try {
      final folders = await VideoScanner().scanForVideos();

      final allVideos = <VideoFile>[];
      for (final folder in folders) {
        allVideos.addAll(folder.videos);
      }

      allVideos.sort((a, b) => b.dateAdded.compareTo(a.dateAdded));

      if (mounted) {
        setState(() {
          _videos = allVideos;
          _isLoading = false;
          _isScanning = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _isScanning = false;
        });
      }
    }
  }

  Future<void> _openVideoPlayer(VideoFile video) async {
    if (_isNavigating) return;

    setState(() => _isNavigating = true);

    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => VideoPlayerScreen(video: video),
      ),
    );

    setState(() => _isNavigating = false);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      body: SafeArea(
        child: Column(
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
              child: Row(
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: Colors.red.shade50,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Icon(
                      Icons.favorite,
                      color: Colors.red.shade500,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'All Videos',
                          style: TextStyle(
                            fontSize: 24,
                            fontWeight: FontWeight.bold,
                            letterSpacing: -0.5,
                          ),
                        ),
                        Text(
                          '${_videos.length} videos found',
                          style: TextStyle(
                            fontSize: 14,
                            color: Colors.grey.shade600,
                          ),
                        ),
                      ],
                    ),
                  ),
                  _buildIconButton(Icons.filter_list, () {}),
                ],
              ),
            ),

            // Video List
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : _videos.isEmpty
                      ? _buildEmptyState()
                      : RefreshIndicator(
                          onRefresh: _loadAllVideos,
                          child: ListView.builder(
                            padding: const EdgeInsets.symmetric(horizontal: 24),
                            itemCount: _videos.length,
                            itemBuilder: (context, index) {
                              return VideoCard(
                                video: _videos[index],
                                onTap: () => _openVideoPlayer(_videos[index]),
                                isLoading: _isNavigating,
                              );
                            },
                          ),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: SingleChildScrollView(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.video_library_outlined,
              size: 80,
              color: Colors.grey.shade400,
            ),
            const SizedBox(height: 20),
            Text(
              'No videos found',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: Colors.grey.shade700,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Pull down to scan for videos',
              style: TextStyle(
                fontSize: 14,
                color: Colors.grey.shade500,
              ),
            ),
            const SizedBox(height: 32),
            ElevatedButton.icon(
              onPressed: () {
                final demoFolder = VideoScanner.getDemoData().first;
                setState(() {
                  _videos = demoFolder.videos;
                });
              },
              icon: const Icon(Icons.play_circle_outline),
              label: const Text('Try Demo Mode'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF6366F1),
                foregroundColor: Colors.white,
                padding:
                    const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIconButton(IconData icon, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(14),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 10,
              offset: const Offset(0, 2),
            ),
          ],
          border: Border.all(color: Colors.grey.shade200),
        ),
        child: Icon(icon, color: Colors.grey.shade600, size: 22),
      ),
    );
  }
}

class VideoCard extends StatelessWidget {
  final VideoFile video;
  final VoidCallback onTap;
  final bool isLoading;

  const VideoCard({
    super.key,
    required this.video,
    required this.onTap,
    this.isLoading = false,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: isLoading ? null : onTap,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.03),
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
              borderRadius:
                  const BorderRadius.vertical(top: Radius.circular(20)),
              child: AspectRatio(
                aspectRatio: 16 / 9,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    Container(
                      color: Colors.grey.shade200,
                      child: const Icon(
                        Icons.video_file,
                        size: 48,
                        color: Colors.grey,
                      ),
                    ),
                    // Resolution Badge
                    Positioned(
                      top: 12,
                      left: 12,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.black.withOpacity(0.5),
                          borderRadius: BorderRadius.circular(8),
                          border:
                              Border.all(color: Colors.white.withOpacity(0.2)),
                        ),
                        child: Text(
                          video.resolution,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ),
                    ),
                    // Size Badge
                    Positioned(
                      bottom: 12,
                      right: 12,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.black.withOpacity(0.5),
                          borderRadius: BorderRadius.circular(8),
                          border:
                              Border.all(color: Colors.white.withOpacity(0.2)),
                        ),
                        child: Text(
                          video.formattedSize,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                    // Play Button Overlay
                    Positioned.fill(
                      child: Container(
                        color: Colors.black.withOpacity(0.1),
                        child: Center(
                          child: Container(
                            width: 64,
                            height: 64,
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.2),
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: Colors.white.withOpacity(0.3),
                                width: 2,
                              ),
                            ),
                            child: const Icon(
                              Icons.play_arrow,
                              color: Colors.white,
                              size: 32,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // Video Info - FIXED: SingleChildScrollView to prevent overflow
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    video.title,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFF1E293B),
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Icon(
                        Icons.folder_outlined,
                        size: 14,
                        color: Colors.grey.shade500,
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(
                          video.folderName,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: Colors.grey.shade500,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        '• ${video.formattedDate}',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                          color: Colors.grey.shade500,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
