import 'package:flutter/material.dart';
import '../../../history/services/history_service.dart';
import '../../../settings/services/app_settings_service.dart';
import '../../controller/player_controller.dart';

/// Helper class for managing resume playback functionality.
///
/// This class handles checking for saved playback positions and
/// displaying a styled resume snackbar to the user.
class ResumePlaybackHelper {
  ResumePlaybackHelper._();

  /// Checks for saved playback position and resumes if appropriate.
  ///
  /// Returns the saved position if resuming, null otherwise.
  static Future<Duration?> checkAndResumePlayback({
    required PlayerController controller,
    required Duration totalDuration,
    required String videoId,
  }) async {
    if (!AppSettingsService.autoResumePlayback) {
      return null;
    }

    // Wait for duration to be loaded (max 10 seconds)
    var attempts = 0;
    var effectiveDuration = totalDuration;
    while (effectiveDuration.inSeconds == 0 && attempts < 100) {
      await Future.delayed(const Duration(milliseconds: 100));
      attempts++;
      effectiveDuration = controller.duration;
    }

    // If duration is still 0, video failed to load
    if (effectiveDuration.inSeconds == 0) return null;

    // Wait for buffering to complete (max 5 seconds)
    attempts = 0;
    while (controller.isBuffering && attempts < 50) {
      await Future.delayed(const Duration(milliseconds: 100));
      attempts++;
    }

    // Extra delay to ensure video is ready for seeking
    await Future.delayed(const Duration(milliseconds: 300));

    // Get the saved position
    final savedPosition = await HistoryService.getLastPosition(videoId);
    if (savedPosition == null) return null;

    // Check if we should resume (position > 5s from start and > 30s from end)
    final totalSeconds = effectiveDuration.inSeconds;
    final positionSeconds = savedPosition.inSeconds;
    final remainingSeconds = totalSeconds - positionSeconds;

    // Don't resume if at the beginning or near the end
    if (positionSeconds < 5 || remainingSeconds <= 30) {
      return null;
    }

    // Seek to the saved position
    controller.seek(savedPosition);

    return savedPosition;
  }

  /// Shows a styled resume snackbar with custom container styling.
  ///
  /// Returns a controller that can be used to dismiss the snackbar.
  static ScaffoldFeatureController<SnackBar, SnackBarClosedReason>
      showResumeSnackbar({
    required BuildContext context,
    required PlayerController controller,
    required Duration position,
  }) {
    final scaffoldMessenger = ScaffoldMessenger.of(context);
    final formattedTime = formatTime(position);

    // Hide any existing snackbars first
    scaffoldMessenger.hideCurrentSnackBar();

    return scaffoldMessenger.showSnackBar(
      SnackBar(
        content: Row(
          children: [
            const Icon(Icons.play_arrow, color: Colors.white70, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Resume from $formattedTime',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
            TextButton(
              onPressed: () {
                controller.seek(Duration.zero);
                scaffoldMessenger.hideCurrentSnackBar();
              },
              style: TextButton.styleFrom(
                padding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              child: const Text(
                'Restart',
                style: TextStyle(color: Colors.white70, fontSize: 13),
              ),
            ),
            const SizedBox(width: 4),
            InkWell(
              onTap: () => scaffoldMessenger.hideCurrentSnackBar(),
              borderRadius: BorderRadius.circular(12),
              child: const Padding(
                padding: EdgeInsets.all(4),
                child: Icon(Icons.close, color: Colors.white54, size: 18),
              ),
            ),
          ],
        ),
        duration: const Duration(seconds: 4),
        backgroundColor: Colors.white12,
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.only(bottom: 120, left: 16, right: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        dismissDirection: DismissDirection.down,
        elevation: 0,
      ),
    );
  }

  /// Safely closes a resume snackbar.
  static void closeSnackbar(
      ScaffoldFeatureController<SnackBar, SnackBarClosedReason>? controller) {
    controller?.close();
  }
}
