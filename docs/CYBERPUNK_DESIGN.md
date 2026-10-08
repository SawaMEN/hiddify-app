# Cyberpunk glass design

The Android UI uses a fixed cyan/magenta/mint palette, layered translucent panels, specular borders and colored shadows. New installs default to dark mode; saved light, system and black theme choices remain supported. Unbounded Semibold is bundled locally for headings with Cyrillic coverage; Manrope retains static body weights.

Shared components apply the finish to settings, profiles, privacy, community selections, inputs, buttons, dialogs and bottom sheets. All monochrome UI icons use a halo behind the existing vector silhouette. Installed app artwork keeps its original colors inside a glass frame. Removed accidentally opaque SVG viewport paths from 26 Material icon resources. The square Hetzner brand logo is intentional and retained.

Page transitions, button presses, ambient background drift, the connection orbit and community row placement animate without modifying native scroll physics. Ambient and orbit animation state is read during drawing rather than recomposing lists each frame. Motion respects Android animator scale and TalkBack. Panels use translucent tint and reflections; this implementation does not capture or blur the underlying screen.

Validation completed: standalone Kotlin resource inventory; existing Dart parity source inventory; static font weights and Cyrillic glyph coverage; rasterized 133 UI vector resources for visible glyphs and transparent viewport corners; changed Kotlin files have no additional tree-sitter parser errors relative to the base commit (some existing grammar limitations remain).

Android compilation, device screenshots, scrolling performance and live VPN behavior still require the Android build/device environment. APK completion was not awaited. The follow-up synchronizes VPN lifecycle, runtime options, Psiphon and profile chain stages from main at 5dd4817. The core pin is retained at main's 96e19a9.

Follow-up polish: shared buttons on empty-profile/import/profile-list/quick-settings screens; glass import tiles and free-profile rows; animated profile and outbound ordering; vector traffic icons; theme-aware log colors; cached icon halos; animations pause when the host lifecycle is no longer resumed. Source and resource checks pass; device rendering and performance remain pending.
