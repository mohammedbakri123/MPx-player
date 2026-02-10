import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../domain/entities/video_folder.dart';
import '../../controller/library_controller.dart';
import 'folder_detail_screen.dart';
import '../widgets/home_header.dart';
import '../widgets/home_section_header.dart';
import '../widgets/home_content.dart';
import '../widgets/home_fab.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  @override
  void initState() {
    super.initState();
    // Load videos on screen initialization
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<LibraryController>().load();
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
}
