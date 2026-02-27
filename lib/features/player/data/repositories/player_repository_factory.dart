import '../domain/player_type.dart';
import '../data/repositories/vlc_player_repository.dart';
import '../data/repositories/media_kit_player_repository.dart';
import '../domain/repositories/player_repository.dart';

/// Factory function to create a [PlayerRepository] based on the specified [playerType].
///
/// This allows switching between different player backend implementations
/// (VLC or MediaKit) while maintaining the same interface.
PlayerRepository createPlayerRepository(PlayerType playerType) {
  switch (playerType) {
    case PlayerType.vlc:
      return VlcPlayerRepository();
    case PlayerType.mediaKit:
      return MediaKitPlayerRepository();
  }
}
