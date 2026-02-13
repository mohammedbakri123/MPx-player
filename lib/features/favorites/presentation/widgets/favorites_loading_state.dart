import 'package:flutter/material.dart';

class FavoritesLoadingState extends StatelessWidget {
  const FavoritesLoadingState({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(child: CircularProgressIndicator());
  }
}
