///TODO: Implement a service to manage the last played video using shared_preferences.

// import 'package:shared_preferences/shared_preferences.dart';
// import '../models/last_played_video.dart';

// class LastPlayedService {
//   static const String _lastPlayedVideoKey = 'last_played_video';

//   static late SharedPreferences _prefs;

//   static Future<void> init() async {
//     _prefs = await SharedPreferences.getInstance();
//   }

//   // Save the last played video
//   static Future<bool> saveLastPlayedVideo(LastPlayedVideo video) async {
//     final jsonString = video.toJson();
//     return await _prefs.setString(_lastPlayedVideoKey, jsonString);
//   }

//   // Get the last played video
//   static LastPlayedVideo? getLastPlayedVideo() {
//     final jsonString = _prefs.getString(_lastPlayedVideoKey);
//     if (jsonString != null) {
//       return LastPlayedVideo.fromJson(jsonString);
//     }
//     return null;
//   }

//   // Clear the last played video
//   static Future<bool> clearLastPlayedVideo() async {
//     return await _prefs.remove(_lastPlayedVideoKey);
//   }

//   // Check if there's a last played video
//   static bool hasLastPlayedVideo() {
//     return _prefs.getString(_lastPlayedVideoKey) != null;
//   }
// }
