import 'package:flutter/material.dart';

import '../../domain/enums/quality_preference.dart';

class QualitySelectorDropdown extends StatelessWidget {
  const QualitySelectorDropdown({
    super.key,
    required this.value,
    required this.onChanged,
  });

  final QualityPreference value;
  final ValueChanged<QualityPreference?> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<QualityPreference>(
      initialValue: value,
      decoration: InputDecoration(
        labelText: 'Quality',
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
        ),
      ),
      items: QualityPreference.values
          .map(
            (quality) => DropdownMenuItem<QualityPreference>(
              value: quality,
              child: Text(quality.label),
            ),
          )
          .toList(growable: false),
      onChanged: onChanged,
    );
  }
}
