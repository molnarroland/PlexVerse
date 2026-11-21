# Changelog

All notable changes to PlexVerse. Version names come from git tags (see `app/build.gradle.kts`).

## [0.1.0] - 2026-11-21

The first PlexVerse release: an Android TV client that merges every Plex server your account reaches
into one library. It started as the `plexverse` branch of embergabor's
[Simplex](https://github.com/embergabor/simplex) and is now its own app (`hu.molnarroland.plexverse`),
built on simplex-core 0.1.2.

### Added

- **Every server, one library.** Movies and shows from all your servers are merged; a title on several
  servers shows once (×N) and plays from the best one that answers, with progress written to every copy.
  Hold OK › *Play from…* lists each copy with its server, quality, audio languages and speed.
- **Server ranking.** Servers are ordered for you (yours before shared, local before remote, faster
  first), with a manual order as an override.
- **Sign-in and profiles.** Sign in with a plex.tv/link code or by scanning a QR code. *Who's watching?*
  uses Plex Home users, with their PINs, restrictions and per-profile tokens on every server. Hold Back
  on Home to switch.
- **Categories.** Movies and Series fill themselves; your own categories (Documentaries, Anime…) live
  under Others, one of them pinned to the main menu, plus Hidden. *Manage categories* with icon buttons
  for Pin, Move, Rename and Delete.
- **Grids.** Sort by A–Z (with a letter rail), Recently added, Year or Unwatched first, remembered per
  category and per profile. Other-video libraries are browsed by folder.
- **Search** across every server, including episode descriptions, typed or spoken.
- **Kids mode.** A Kids profile turns the app into big posters of chosen libraries, with an age ceiling,
  *Surprise me!*, an episode limit, and no menu or Settings. A grown-up holds Back for 3 seconds and
  enters the owner's Plex PIN (or answers a sum) to leave.
- **Night and day look.** A deep-space night and a pale, cloudy day sky (mint for Kids), switching at
  sunset and sunrise from the TV's IP address or a place set by hand; or always Light or Dark. Animated
  planet mark, two-stage splash, new app icon and TV banner.
- **Player** from simplex-core: direct play on the TV's hardware decoder, audio passthrough to the
  receiver (or Plex's 5.1 conversion), 10 s jumps, scrubbing with preview thumbnails, chapters, skip
  intro, per-show audio and subtitle languages.
- **Website** (`docs/`, GitHub Pages) with a slideshow of every screen in night and day, and a README
  with screenshots and a features table.

### Changed

- The Plex connection, player and shared look come from simplex-core (from simplex-maven) instead of
  copies inside the app.
- The in-app GitHub update check and installer are gone; releases are installed by hand with
  `adb install -r`.
- The version name comes from the latest git tag instead of `0.2.<commit count>`.

### Known issues

- Not yet tested end to end on a TV with a real account: profiles and PINs, the held-Back gestures, Kids
  mode limits and Auto theme still need checking.
- With some files whose default track is TrueHD, an AC3 compatibility track is decoded instead of passed
  through.
- No lag prompt to switch servers mid-playback, and the local Kids profile shares the account's
  progress: both need hooks in simplex-core.
- Android 12+'s system splash is always the night one.

[0.1.0]: https://github.com/molnarroland/PlexVerse/releases/tag/0.1.0
