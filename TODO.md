# TODO

## The rethink, built (2026-11-20), to check on the TV

Not tried with a real account yet: only start-up, the splash, the night and day skies and the sign-in
screen (with a live plex.tv code and its QR) were run, on a phone emulator.

- Sign-in → Who's watching (Plex Home) → first-run Servers & categories → Home. Switching profiles with a
  PIN; a managed user's servers and Continue watching are their own.
- Hold Back: 1 s on Home switches profile; 3 s in Kids mode opens the grown-ups' gate. Check the remote
  sends repeats for a held Back.
- Kids: the age ceiling (Plex's contentRating, read from the library lists), the episode limit, the mic
  narrowing the posters.
- Theme Auto from the IP address, a place set by hand, the switch waiting while a video plays.

## Built 2026-11-21, to check on the TV

- Duplicates: legacy-agent ids (`com.plexapp.agents.imdb://…`) now match current-agent copies, and the
  same title + year always merges. Check the titles that showed twice are one now.
- Who's watching with more than 10 Plex Home users (the "Others" face), the lock badge, a wrong PIN.
- First-run categories: Movies and Series only, then "Make other categories too?".

## Not built: needs simplex-core

- **Lag prompt during playback** ("Lagging on NAS. Continue on Office, same Hungarian audio?"). The player
  is simplex-core's `PlayerActivity`, which reports nothing back (rebuffers, download rate, first frame)
  and can't be extended. Needs a hook in the core: a playback-stats callback or broadcast, and a way to
  hand it a new source at the current position. Servers are already ranked by their measured answer time.
- **The local Kids profile's own progress.** It shares the account's token, and the player reports
  progress itself; making it not report needs a player option in the core.
- **The system splash in day colours.** Android 12's splash is drawn from the manifest theme before the
  app runs, so it's always the night one; the app's own splash follows the theme.

## Built, to check on the TV (2026-10-31)

Player:
- Audio/Subtitles buttons read "Japanese · DTS-HD MA Stereo"; a track's own title only when it says more.
- The audio/subtitle remote buttons show the info box alone, with the stepped track underneath.
- Spinner while loading, buffering or waiting on Plex (after 0.5 s).
- OK on the progress bar pauses and resumes (still jumps while scrubbing).
- "Skip intro" / "Next episode" from Plex's markers while the controls are hidden (OK presses it). The
  server has intro markers; no credits markers seen yet.
- Resume only from Continue watching and the show page's Play; everything else starts from 0:00.
- Audio and subtitle language (or subtitles off) remembered per show.
- Direct play errors: one retry from the same spot, then Plex's conversion, then the error.
- Paused 5 minutes: the screen is let go, so the TV's screensaver can run.
- Stopped in the end credits (or past 95%): marked watched.

Screens:
- Hold-OK menu: Resume from …, Remove from Continue watching, Go to show, Info (short summary popup).
- Library grid: sort (A–Z, Recently added, Year), an A–Z jump column, two-line poster titles.
- Show page: moving up/down into a season lands on its first episode; episode cards show the air date.
- Search keeps the last search until cleared, with recent searches as quick picks.
- Settings (Home's tile, was Edit): libraries on Home, clear caches, sign out, build info.

- Settings → Wake server: the server's MAC address typed in (separators optional), with "Wake now". With
  one saved, the "couldn't reach" screens get a focused "Wake server" button. Not tried on a sleeping server.
- Info popup lists every audio track (one per line) and every subtitle language, no "+ N more".
- Video the TV can't decode (VC-1 on the Sony) goes to Plex's conversion instead of a black picture.
- The readout says what the TV is handed: "passthrough as DTS", "as E-AC3", …
- Left/right with the player's controls hidden jump 10 s either way at once, with a bubble ("+30 s ·
  1:02:15 / 1:45:00"); held, they scrub as before.
- The format readout is hidden unless Settings → Playback details shows it; the remote's Info button switches it.
- Settings → Unwatched episodes: blurs the pictures of episodes not started (show page, search). Plex blurs them.
- Show posters count their unwatched episodes in the corner.
- The opening plays once per start of the app, not on every return to it.
- The remote's mic opens Google's voice input; the words are searched for in Search.
- Back from the player, Continue watching gives focus back to the card the video started from (or the one in
  its place once finished).

## What the Sony gives apps (checked 2026-11-02)

The TV's own apps reach the Marantz through the TV's audio system, not straight over eARC:
- Bitstream: AC3, E-AC3 (Atmos too) and DTS. DTS-HD is announced and accepted, but only its core comes out
  (tried by handing it over as DTS-HD, then reverted: Matroska's DTS-HD goes out as DTS, which is the same).
- No TrueHD or Dolby MAT, and PCM in stereo only: decoded audio is always 2.0.
- No VC-1 video decoder (only MediaTek's WMV one, which ExoPlayer doesn't use for VC-1).

Updates:
- Release key in `~/.plexverse-release` (back it up: without it no release can update the TV). Version names come
  from the latest git tag (`git describe`), version codes are the commit count. `tools/release.sh` builds, checks the signature and publishes a GitHub release.
- No in-app update check any more: releases are installed by hand (`adb install -r`).
- Switching the TV to the release key needs one uninstall and reinstall (signing in again).

## Open

- **Sample 08: the AC3 compatibility track is decoded instead of passed through.** The HDMI output accepts
  AC3 and sample 01 (AC3 only) passes through; here ExoPlayer gave the AC3 track to the FFmpeg decoder. The
  file's default track is TrueHD, likely related. Debug builds log track mapping (logcat tag EventLogger):
  replay 08, pick the AC3 track, read the log.

## Later

- **Brighter SDR preview thumbnails.** The scrubbing thumbnails are drawn exactly as Plex made them (checked
  on Totoro: same levels as the film's own frames, and on screen). They still look dark and faded next to
  the video, because the Sony's picture mode brightens and adds contrast and colour to the video layer only,
  not to the app's graphics on top. Idea: a per-channel lookup like the HDR tone-map in `PreviewThumbs.kt`
  (deeper blacks, a bit more contrast and saturation), tuned by eye on the TV.

Decided against: moving image subtitles above the open controls, an "Up next" countdown, a clock, more rows
on Home, a movie info page (no detail pages is a core goal), Back twice to exit, automating the TrueHD script.
