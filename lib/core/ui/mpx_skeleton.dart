import 'package:flutter/material.dart';

import '../theme/mpx_ui_tokens.dart';

/// Static loading placeholders in the same shape as the real
/// grid / list, so loading never flashes a spinner in the middle
/// of the screen. No animation controller: keep it cheap and
/// reduced-motion safe.
class MpxSkeletonGrid extends StatelessWidget {
  const MpxSkeletonGrid({super.key, this.itemCount = 6});

  final int itemCount;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;

    return GridView.builder(
      padding: const EdgeInsets.symmetric(
        horizontal: MpxSpacing.lg,
        vertical: MpxSpacing.sm,
      ),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 10,
        crossAxisSpacing: 10,
        childAspectRatio: 0.85,
      ),
      itemCount: itemCount,
      itemBuilder: (BuildContext context, int index) {
        return Container(
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest.withValues(alpha: 0.5),
            borderRadius: BorderRadius.circular(MpxRadius.lg),
          ),
        );
      },
    );
  }
}

class MpxSkeletonList extends StatelessWidget {
  const MpxSkeletonList({super.key, this.itemCount = 8});

  final int itemCount;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;

    return ListView.builder(
      padding: const EdgeInsets.symmetric(
        horizontal: MpxSpacing.md,
        vertical: MpxSpacing.sm,
      ),
      itemCount: itemCount,
      itemBuilder: (BuildContext context, int index) {
        return Container(
          height: 64,
          margin: const EdgeInsets.only(bottom: MpxSpacing.sm),
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest.withValues(alpha: 0.5),
            borderRadius: BorderRadius.circular(MpxRadius.md),
          ),
        );
      },
    );
  }
}
