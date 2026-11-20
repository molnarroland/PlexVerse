# PlexVerse

Every Plex server your account reaches, as one set of libraries, on Android TV. PlexVerse merges the
movies and shows of all of them, plays each title from the best server that has it, and gives every
person in the house their own profile, with a Kids mode a child can't wander out of.

A deliberately simple client: no recommendations, descriptions, cast or reviews. Built for a Sony Bravia
(Google TV) sending audio over eARC to an AV receiver, but it should run on any Android TV 8.0+.
Package: `hu.molnarroland.plexverse`.

## What it does

- **Start:** the mark (a planet with a play symbol, three servers in its orbit) on the night sky while the
  servers answer. Sign in once with a plex.tv/link code, or scan the QR code with a phone.
- **Who's watching?** The profiles are Plex Home's users, so watched state, progress, restrictions and
  PINs come from Plex. Picking one switches to that person's token on every server. Start-up either asks
  or starts as the last person; holding Back on Home switches. Without Plex Home there's one profile, plus
  an optional local Kids profile.
- **Home:** Continue watching across every server, then what's new in Movies and Series (and the pinned
  category). The main menu: Search, Home, Movies, Series, one pinned category, Others (a popup with the
  rest and Manage categories), Settings.
- **Servers & categories:** two steps, with "Looks right" on both. Servers are ranked for you: yours before
  shared, on this network before remote, faster first (Move overrides it). Movies and Series fill
  themselves from every movie and show library; your own categories (Documentaries, Anime…) sit under
  Others, one of them pinned to the menu. OK on a library moves it to a category or Hidden.
- **Grids:** A–Z (with a letter rail), Recently added, Year or Unwatched first, remembered per category and
  per profile. A title on several servers shows once (×N), plays from the highest-ranked server that
  answers, and its progress goes to every copy. Hold OK › Play from… lists each copy with its server,
  quality, audio languages and speed. Other-video libraries are browsed by folder.
- **No detail pages.** OK plays from the beginning. Hold OK for a menu: resume, mark watched or unwatched,
  favourites, and Info. Shows: Play (where you left off), Shuffle, favourite episodes, a row per season.
- **Search** across every server, including episode descriptions, typed or spoken into the remote's mic.
- **Kids mode:** a Kids profile opens it as the whole app: big posters of the libraries a grown-up picked,
  nothing rated above its age ceiling, Surprise me!, its own Continue watching, and at most N episodes in
  a row. No menu or Settings, and Back does nothing; a grown-up holds Back for 3 seconds and enters the
  owner's Plex PIN (or answers a sum).
- **Light and dark:** Auto (the default) is dark after sunset and light after sunrise, from the TV's IP
  address or a place set by hand; or always Light or Dark. By day the sky is sunlit and the planet a sun.
- **Player** (Media3 ExoPlayer, from simplex-core): direct play through the TV's hardware decoder; audio
  passed through as a bitstream when the TV and receiver accept it, otherwise converted by Plex to 5.1;
  10 s jumps and scrubbing with preview thumbnails; chapters; skip intro; per-show audio and subtitle
  languages; an optional readout of what reaches the receiver.
- **Made for slow TVs:** the last screen stays until the next has its content, then crossfades; the sky
  is drawn once into a picture; focus lifts the card with a shadow. 60 fps on a TV's modest GPU and CPU.

## Build and install

You need JDK 17+ and the Android SDK.

```bash
./gradlew assembleRelease
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with a key kept outside the repo (`~/.plexverse-release`, see
`app/build.gradle.kts`); without it they fall back to the debug key. `tools/release.sh` publishes a
signed build as a GitHub release of `molnarroland/PlexVerse`, which release builds offer to install.

## The core

The Plex connection, the player and the cards come from simplex-core, used only as published to
[simplex-maven](https://github.com/embergabor/simplex-maven), at the version named in
`app/build.gradle.kts`. Everything else (profiles, merging and ranking, categories, Kids mode, the look)
is PlexVerse's own, in `app/src/main/java/hu/molnarroland/plexverse`.

## Server script: compatibility tracks for TrueHD

[`tools/add-compat-tracks.sh`](tools/add-compat-tracks.sh) runs on the Plex server. It adds an AC3 5.1
640 kbps track next to TrueHD tracks that have no same-language AC3, E-AC3 or DTS track, so those files
can play with surround sound passed through, without Plex converting anything. Everything else in the
file is copied untouched. Start with `--dry-run`, and see `--help` for the rest.

## License

[MIT](LICENSE)
