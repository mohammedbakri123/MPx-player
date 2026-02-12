/// Formats a Duration into a human-readable time string.
///
/// Examples:
/// - 90 seconds -> "01:30"
/// - 3665 seconds -> "01:01:05"
String formatTime(Duration duration) {
  String twoDigits(int n) => n.toString().padLeft(2, '0');
  final hours = duration.inHours;
  final mins = twoDigits(duration.inMinutes.remainder(60));
  final secs = twoDigits(duration.inSeconds.remainder(60));
  return hours > 0 ? '${twoDigits(hours)}:$mins:$secs' : '$mins:$secs';
}
