enum QualityPreference {
  auto,
  p1080,
  p720,
  p480,
  audioOnly,
}

extension QualityPreferenceX on QualityPreference {
  String get label {
    switch (this) {
      case QualityPreference.auto:
        return 'Auto';
      case QualityPreference.p1080:
        return '1080p';
      case QualityPreference.p720:
        return '720p';
      case QualityPreference.p480:
        return '480p';
      case QualityPreference.audioOnly:
        return 'Audio only';
    }
  }

  String get formatSelector {
    switch (this) {
      case QualityPreference.auto:
        return 'best[ext=mp4]/best';
      case QualityPreference.p1080:
        return 'best[height<=1080][ext=mp4]/best[height<=1080]/best';
      case QualityPreference.p720:
        return 'best[height<=720][ext=mp4]/best[height<=720]/best';
      case QualityPreference.p480:
        return 'best[height<=480][ext=mp4]/best[height<=480]/best';
      case QualityPreference.audioOnly:
        return 'bestaudio[ext=m4a]/bestaudio/best';
    }
  }
}
