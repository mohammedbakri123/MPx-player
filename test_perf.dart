import 'dart:io';

void main() async {
  final stopwatch = Stopwatch()..start();
  int count1 = 0;
  
  try {
    await for (final entity in Directory('/home/mohammed/Documents').list(recursive: true, followLinks: false).handleError((e){})) {
      count1++;
      if (count1 > 50000) break;
    }
  } catch(e) {}
  
  print('Async list: ${stopwatch.elapsedMilliseconds}ms, count: $count1');
  
  stopwatch.reset();
  int count2 = 0;
  try {
    for (final entity in Directory('/home/mohammed/Documents').listSync(recursive: true, followLinks: false)) {
      count2++;
      if (count2 > 50000) break;
    }
  } catch (e) { print(e); }
  print('Sync list: ${stopwatch.elapsedMilliseconds}ms, count: $count2');
}
