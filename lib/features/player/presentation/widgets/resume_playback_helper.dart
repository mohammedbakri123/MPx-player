import 'package:flutter/material.dart';
import '../../../history/services/history_service.dart';
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
    // Wait for duration to be loaded (max 10 seconds — large files need more time)
    var attempts = 0;
    while (totalDuration.inSeconds == 0 && attempts < 100) {
      await Future.delayed(const Duration(milliseconds: 100));
      attempts++;
    }

    // If duration is still 0, video failed to load
    if (totalDuration.inSeconds == 0) return null;

    // Get the saved position
    final savedPosition = await HistoryService.getLastPosition(videoId);
    if (savedPosition == null) return null;

    // Check if we should resume (position > 5s from start and > 30s from end)
    final totalSeconds = totalDuration.inSeconds;
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
        content: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                Colors.indigo.shade700,
                Colors.indigo.shade900,
              ],
              begin: Alignment.centerLeft,
              end: Alignment.centerRight,
            ),
            borderRadius: BorderRadius.circular(12),
            boxShadow: [
              BoxShadow(
                color: Colors.indigo.withValues(alpha: 0.4),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Row(
            children: [
              // Play icon
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.play_arrow_rounded,
                  color: Colors.white,
                  size: 20,
                ),
              ),
              const SizedBox(width: 12),
              // Text content
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Resumed from',
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.8),
                        fontSize: 12,
                      ),
                    ),
                    Text(
                      formattedTime,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              // Restart button
              TextButton(
                onPressed: () {
                  controller.seek(Duration.zero);
                },
                style: TextButton.styleFrom(
                  backgroundColor: Colors.white.withValues(alpha: 0.2),
                  foregroundColor: Colors.white,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: const Text(
                  'Restart',
                  style: TextStyle(fontSize: 13),
                ),
              ),
            ],
          ),
        ),
        duration: const Duration(seconds: 5),
        backgroundColor: Colors.transparent,
        elevation: 0,
        behavior: SnackBarBehavior.floating,
        margin: const EdgeInsets.only(bottom: 80, left: 16, right: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        dismissDirection: DismissDirection.down,
      ),
    );
  }

  /// Safely closes a resume snackbar.
  static void closeSnackbar(
      ScaffoldFeatureController<SnackBar, SnackBarClosedReason>? controller) {
    controller?.close();
  }
}
