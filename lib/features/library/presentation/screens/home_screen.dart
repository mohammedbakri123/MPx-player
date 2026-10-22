import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../controller/library_controller.dart';
import '../widgets/video_list_view.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
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
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('MPx')),
      body: Selector<LibraryController, LibraryStatus>(
        selector: (BuildContext context, LibraryController controller) =>
            controller.status,
        builder: (BuildContext context, LibraryStatus status, Widget? child) {
          return switch (status) {
            LibraryStatus.loading => const _LoadingView(),
            LibraryStatus.needsPermission => const _PermissionView(),
            LibraryStatus.empty => const _EmptyView(),
            LibraryStatus.failure => const _FailureView(),
            LibraryStatus.ready => const VideoListView(),
          };
        },
      ),
    );
  }
}

class _LoadingView extends StatelessWidget {
  const _LoadingView();

  @override
  Widget build(BuildContext context) {
    return const Center(child: CircularProgressIndicator());
  }
}

class _PermissionView extends StatelessWidget {
  const _PermissionView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const Icon(Icons.video_library_outlined, size: 64),
          const SizedBox(height: 16),
          const Text('Media access is needed to build your library'),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => context.read<LibraryController>().load(),
            child: const Text('Allow media access'),
          ),
        ],
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return const Center(child: Text('No videos found on this device'));
  }
}

class _FailureView extends StatelessWidget {
  const _FailureView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const Text('Could not load your library'),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => context.read<LibraryController>().load(),
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}
