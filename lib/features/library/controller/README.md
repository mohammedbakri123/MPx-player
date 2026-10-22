Ctrl-side: ChangeNotifier controllers live here. Contract with the rest of the tree
(see ../../ARCHITECTURE.md, "State Management"): store every StreamSubscription,
guard notifyListeners after async gaps with a _disposed flag, narrow rebuild
slices via Selector on the UI side, debounced persistence.
