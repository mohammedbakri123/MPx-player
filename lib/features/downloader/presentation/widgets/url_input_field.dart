import 'package:flutter/material.dart';

class UrlInputField extends StatelessWidget {
  const UrlInputField({
    super.key,
    required this.controller,
    required this.onSubmit,
    this.isBusy = false,
  });

  final TextEditingController controller;
  final VoidCallback onSubmit;
  final bool isBusy;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      decoration: InputDecoration(
        labelText: 'Video URL',
        hintText: 'Paste a YouTube or supported site URL',
        prefixIcon: const Icon(Icons.link_rounded),
        suffixIcon: IconButton(
          onPressed: isBusy ? null : onSubmit,
          icon: isBusy
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.search_rounded),
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
        ),
      ),
      textInputAction: TextInputAction.search,
      onSubmitted: (_) => onSubmit(),
    );
  }
}
