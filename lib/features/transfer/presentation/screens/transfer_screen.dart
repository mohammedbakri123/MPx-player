import 'package:flutter/material.dart';

import '../../../../core/theme/mpx_ui_tokens.dart';

/// Transfer page, MX structure in MPx's own code.
///
/// Contains a downloader placeholder, as requested: a link
/// field and a download button that are visually present
/// but disabled, with a clear "not built yet" note.
/// No download is started, faked, or queued — the
/// downloader feature itself does not exist yet.
class TransferScreen extends StatelessWidget {
  const TransferScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Transfer')),
      body: ListView(
        padding: const EdgeInsets.all(MpxSpacing.page),
        children: <Widget>[
          const Text(
            'Downloader',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: MpxSpacing.sm),
          const Text(
            'Placeholder only — downloads are not built yet.',
            style: TextStyle(color: Colors.white70),
          ),
          const SizedBox(height: MpxSpacing.lg),
          const TextField(
            enabled: false,
            decoration: InputDecoration(
              prefixIcon: Icon(Icons.link_rounded),
              hintText: 'Paste a video link here...',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: MpxSpacing.lg),
          FilledButton.icon(
            icon: const Icon(Icons.download_rounded),
            label: const Text('Download'),
            onPressed: null,
          ),
          const SizedBox(height: MpxSpacing.xxl),
          const Text(
            'No downloads',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: MpxSpacing.sm),
          const Text(
            'Downloaded files will appear here once the downloader is built.',
            style: TextStyle(color: Colors.white70),
          ),
        ],
      ),
    );
  }
}
