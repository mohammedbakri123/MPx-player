import 'package:flutter_test/flutter_test.dart';

import 'package:mpx/features/library/controller/library_controller.dart';
import 'package:mpx/features/library/domain/entities/video_file.dart';
import 'package:mpx/features/library/domain/repositories/library_repository.dart';

class FakeLibraryRepository implements LibraryRepository {
  FakeLibraryRepository({
    this.accessGranted = true,
    this.grantOnRequest = true,
    this.videosToReturn = const <VideoFile>[],
    this.throwOnFetch = false,
  });

  bool accessGranted;
  bool grantOnRequest;
  List<VideoFile> videosToReturn;
  bool throwOnFetch;
  int requestAccessCalls = 0;

  @override
  Future<bool> hasAccess() async => accessGranted;

  @override
  Future<bool> requestAccess() async {
    requestAccessCalls++;
    return grantOnRequest;
  }

  @override
  Future<List<VideoFile>> fetchVideos() async {
    if (throwOnFetch) {
      throw StateError('fetch failed');
    }
    return videosToReturn;
  }
}

VideoFile _video(String id) => VideoFile(
      id: id,
      path: '/storage/emulated/0/Movies/$id.mp4',
      name: '$id.mp4',
      durationMs: 65000,
      sizeBytes: 1024,
      dateModifiedMs: 1700000000000,
      folderPath: '/storage/emulated/0/Movies',
    );

void main() {
  test('load moves to ready with videos when access is already granted',
      () async {
    final FakeLibraryRepository repository = FakeLibraryRepository(
      videosToReturn: <VideoFile>[_video('a'), _video('b')],
    );
    final LibraryController controller = LibraryController(repository);

    await controller.load();

    expect(controller.status, LibraryStatus.ready);
    expect(controller.videos.length, 2);
    expect(repository.requestAccessCalls, 0);
    controller.dispose();
  });

  test('load requests permission once and reports needsPermission if denied',
      () async {
    final FakeLibraryRepository repository = FakeLibraryRepository(
      accessGranted: false,
      grantOnRequest: false,
    );
    final LibraryController controller = LibraryController(repository);

    await controller.load();

    expect(controller.status, LibraryStatus.needsPermission);
    expect(controller.videos, isEmpty);
    expect(repository.requestAccessCalls, 1);
    controller.dispose();
  });

  test('granted request falls through to fetch and becomes ready', () async {
    final FakeLibraryRepository repository = FakeLibraryRepository(
      accessGranted: false,
      grantOnRequest: true,
      videosToReturn: <VideoFile>[_video('c')],
    );
    final LibraryController controller = LibraryController(repository);

    await controller.load();

    expect(controller.status, LibraryStatus.ready);
    expect(controller.videos.first.id, 'c');
    controller.dispose();
  });

  test('empty fetch result reports empty status', () async {
    final LibraryController controller =
        LibraryController(FakeLibraryRepository());

    await controller.load();

    expect(controller.status, LibraryStatus.empty);
    controller.dispose();
  });

  test('fetch failure reports failure status with error', () async {
    final LibraryController controller =
        LibraryController(FakeLibraryRepository(throwOnFetch: true));

    await controller.load();

    expect(controller.status, LibraryStatus.failure);
    expect(controller.state.error, isNotNull);
    controller.dispose();
  });

  test('notifications stop after dispose', () async {
    final FakeLibraryRepository repository = FakeLibraryRepository(
      videosToReturn: <VideoFile>[_video('late')],
    );
    final LibraryController controller = LibraryController(repository);
    int listenerCalls = 0;
    controller.addListener(() => listenerCalls++);

    controller.dispose();
    await controller.load();

    expect(listenerCalls, 0);
  });
}
