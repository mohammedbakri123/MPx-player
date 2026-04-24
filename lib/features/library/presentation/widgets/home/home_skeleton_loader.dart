import 'package:flutter/material.dart';
import '../../../domain/enums/library_view_mode.dart';

/// Skeleton loading screen for home with shimmer effect
/// Matches the exact layout of the actual content
class HomeSkeletonLoader extends StatelessWidget {
  final LibraryViewMode viewMode;
  final int itemCount;

  const HomeSkeletonLoader({
    super.key,
    this.viewMode = LibraryViewMode.list,
    this.itemCount = 8,
  });

  @override
  Widget build(BuildContext context) {
    return switch (viewMode) {
      LibraryViewMode.list => _buildListView(context),
      LibraryViewMode.grid => _buildGridView(context),
    };
  }

  // Widget _buildTreeView(BuildContext context) {
  //   return ListView.builder(
  //     padding: const EdgeInsets.fromLTRB(16, 8, 16, 10),
  //     itemCount: itemCount,
  //     itemBuilder: (context, index) {
  //       return _buildTreeSkeleton(indentLevel: index % 3);
  //     },
  //   );
  // }

  // Widget _buildTreeSkeleton({required int indentLevel}) {
  //   return Padding(
  //     padding: EdgeInsets.only(left: 16.0 * indentLevel, bottom: 12),
  //     child: Row(
  //       children: [
  //         const ShimmerWidget(width: 16, height: 16, borderRadius: 2),
  //         const SizedBox(width: 8),
  //         const ShimmerWidget(width: 20, height: 20, borderRadius: 4),
  //         const SizedBox(width: 10),
  //         Expanded(
  //           child: Column(
  //             crossAxisAlignment: CrossAxisAlignment.start,
  //             children: [
  //               ShimmerWidget(
  //                 width: 120 + (indentLevel * 40).toDouble(),
  //                 height: 14,
  //                 borderRadius: 4,
  //               ),
  //               const SizedBox(height: 6),
  //               const ShimmerWidget(width: 60, height: 10, borderRadius: 4),
  //             ],
  //           ),
  //         ),
  //       ],
  //     ),
  //   );
  // }

  Widget _buildGridView(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        final estimatedCount = ((width + 14) / 190).floor();
        final crossAxisCount = estimatedCount < 2
            ? 2
            : estimatedCount > 4
                ? 4
                : estimatedCount;
        final cardWidth =
            (width - 48 - ((crossAxisCount - 1) * 14)) / crossAxisCount;
        final previewHeight = cardWidth.clamp(132.0, 180.0);
        final mainAxisExtent = previewHeight + 108;

        return GridView.builder(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: crossAxisCount,
            crossAxisSpacing: 14,
            mainAxisSpacing: 14,
            mainAxisExtent: mainAxisExtent,
          ),
          itemCount: itemCount,
          itemBuilder: (context, index) {
            return _buildGridSkeleton(previewHeight);
          },
        );
      },
    );
  }

  Widget _buildListView(BuildContext context) {
    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      itemCount: itemCount,
      itemBuilder: (context, index) {
        return _buildListSkeleton();
      },
    );
  }

  Widget _buildGridSkeleton(double previewHeight) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFFE2E8F0)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildThumbnailSkeleton(
            height: previewHeight,
            borderRadius: 20,
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildTextSkeleton(
                  width: double.infinity,
                  height: 16,
                ),
                const SizedBox(height: 6),
                _buildTextSkeleton(
                  width: 110,
                  height: 12,
                ),
                const SizedBox(height: 14),
                _buildTextSkeleton(
                  width: 86,
                  height: 22,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildListSkeleton() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildThumbnailSkeleton(
            width: 160,
            height: 100,
            borderRadius: 12,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 8),
                _buildTextSkeleton(
                  width: double.infinity,
                  height: 18,
                ),
                const SizedBox(height: 8),
                _buildTextSkeleton(
                  width: 150,
                  height: 14,
                ),
                const SizedBox(height: 8),
                _buildTextSkeleton(
                  width: 100,
                  height: 14,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildThumbnailSkeleton({
    double width = double.infinity,
    double? height,
    double borderRadius = 12,
  }) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: ShimmerWidget(
        width: width,
        height: height,
        borderRadius: borderRadius,
      ),
    );
  }

  Widget _buildTextSkeleton({
    double width = double.infinity,
    double height = 16,
  }) {
    return ShimmerWidget(
      width: width,
      height: height,
      borderRadius: 4,
    );
  }
}

/// Shimmer effect widget
class ShimmerWidget extends StatefulWidget {
  final double? width;
  final double? height;
  final double borderRadius;

  const ShimmerWidget({
    super.key,
    this.width,
    this.height,
    this.borderRadius = 8,
  });

  @override
  State<ShimmerWidget> createState() => _ShimmerWidgetState();
}

class _ShimmerWidgetState extends State<ShimmerWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Container(
          width: widget.width ?? double.infinity,
          height: widget.height ?? 16,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(widget.borderRadius),
            gradient: LinearGradient(
              begin: Alignment(-2 + _controller.value * 4, 0),
              end: const Alignment(-1, 0),
              colors: [
                Colors.grey.shade300,
                Colors.grey.shade100,
                Colors.grey.shade300,
              ],
              stops: const [0.0, 0.5, 1.0],
            ),
          ),
        );
      },
    );
  }
}
