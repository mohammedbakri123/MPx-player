import 'package:flutter_test/flutter_test.dart';
import 'package:mpx/app/app.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
  });

  testWidgets('app builds and shows MPx Library', (WidgetTester tester) async {
    await tester.pumpWidget(const MPxApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    expect(find.text('MPx Library'), findsOneWidget);
  });
}
