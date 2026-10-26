enum LibrarySortOption {
  name,
  date,
  size;

  String get label {
    switch (this) {
      case LibrarySortOption.name:
        return 'Name';
      case LibrarySortOption.date:
        return 'Date Added';
      case LibrarySortOption.size:
        return 'Size';
    }
  }
}
