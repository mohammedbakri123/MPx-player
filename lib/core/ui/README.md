# Shared UI (pure presentation)

Components in this folder render only. They never touch controllers,
data, or the database directly; behaviour comes in as callback props.

- `mpx_section_header.dart` — uppercase tracked section label + count pill.
- `mpx_empty_state.dart` — empty / error / no-results state with an action slot.
- `mpx_skeleton.dart` — grid and list loading placeholders, no animation.

Tokens live in `core/theme/mpx_ui_tokens.dart` (spacing, radius, motion)
and `core/theme/app_theme_tokens.dart` (Midnight dark / Paper light colours).
