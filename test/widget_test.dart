import 'package:flutter_test/flutter_test.dart';

import 'package:mpx/app/app.dart';

void main() {
  testWidgets('app builds and shows library placeholder', (tester) async {
    await tester.pumpWidget(const MPxApp());

    expect(find.text('MPx'), findsOneWidget);
  });
}
