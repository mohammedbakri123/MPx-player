/// Pure UI tokens for the MPx "Midnight Player" look.
///
/// No logic lives here: only spacing, radius, and motion values that
/// every screen and component shares, so the app stops drifting
/// between 12 / 14 / 16 / 20 / 24 radii and ad-hoc paddings.
abstract final class MpxSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double xxl = 24;
  static const double xxxl = 32;

  /// Standard page side padding.
  static const double page = 20;

  /// Standard card inner padding.
  static const double card = 16;
}

abstract final class MpxRadius {
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double artwork = 28;
  static const double pill = 999;
}

abstract final class MpxMotion {
  /// Standard page, sheet, and toggle transitions.
  static const Duration standard = Duration(milliseconds: 300);

  /// Dialogs and small fades.
  static const Duration fast = Duration(milliseconds: 250);

  /// Hero artwork and mini-dock to full-player expansions.
  static const Duration hero = Duration(milliseconds: 450);

  /// Theme dark to light cross-fade.
  static const Duration theme = Duration(milliseconds: 400);

  /// Stagger between list items on first load only.
  static const Duration staggerStep = Duration(milliseconds: 30);
}
