import 'package:flutter/material.dart';

/// Skeleton loading screen for home with shimmer effect
/// Matches the exact layout of the actual content
class HomeSkeletonLoader extends StatelessWidget {
  final bool isGridView;
  final int itemCount;

  const HomeSkeletonLoader({
    super.key,
    this.isGridView = false,
    this.itemCount = 8,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async {},
      child: isGridView
          ? _buildGridView(context)
          : _buildListView(context),
    );
  }

  Widget _buildGridView(BuildContext context) {
    return GridView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 1,
        crossAxisSpacing: 16,
        mainAxisSpacing: 16,
      ),
      itemCount: itemCount,
      itemBuilder: (context, index) {
        return _buildGridSkeleton();
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

  Widget _buildGridSkeleton() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildThumbnailSkeleton(
          height: double.infinity,
          borderRadius: 16,
        ),
        const SizedBox(height: 12),
        _buildTextSkeleton(
          width: double.infinity,
          height: 18,
        ),
        const SizedBox(height: 8),
        _buildTextSkeleton(
          width: 100,
          height: 14,
        ),
      ],
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
