import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollDirection;
import 'package:provider/provider.dart';
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
  final ScrollController _scrollController = ScrollController();
  bool _isFabVisible = true;

  @override
  void initState() {
    super.initState();
    // Load videos on screen initialization
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<LibraryController>().load();
    });
    _scrollController.addListener(_onScroll);
  }

  void _onScroll() {
    if (_scrollController.position.userScrollDirection ==
        ScrollDirection.reverse) {
      if (_isFabVisible) setState(() => _isFabVisible = false);
    } else if (_scrollController.position.userScrollDirection ==
        ScrollDirection.forward) {
      if (!_isFabVisible) setState(() => _isFabVisible = true);
    }
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

            // Folder List/Grid
            Expanded(
              child: HomeContent(
                controller: controller,
                onFolderTap: _openFolder,
                scrollController: _scrollController,
              ),
            ),
          ],
        ),
      ),
      floatingActionButton: AnimatedOpacity(
        opacity: _isFabVisible ? 1.0 : 0.0,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeInOut,
        child: const HomeFAB(),
      ),
    );
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _openFolder(VideoFolder folder) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => FolderDetailScreen(folder: folder),
      ),
    );
  }
}
