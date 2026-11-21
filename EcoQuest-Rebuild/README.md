# EcoQuest group project copy

This is one Java project based on the supplied Unfinished game, its Residential Street gameplay, and the Level 3 Market source. The original menu, illustrated five-region selector, player sprites, and street map remain in place. Each region now opens a cleaning stage.

## Run it

1. Install a 64-bit JDK 17 or newer. VS Code by itself does not include Java. Check in a terminal with `java --version` and `javac --version`.
2. Install Microsoft's **Extension Pack for Java** in VS Code.
3. Open this `EcoQuest-Rebuild` folder in VS Code and press **F5**, or double-click `run.bat`.

If `java` or `javac` is not recognized, install the [Eclipse Temurin JDK 17 for Windows x64](https://adoptium.net/temurin/releases/?version=17&os=windows&arch=x64&package=jdk), then reopen VS Code. Microsoft's [Java in VS Code guide](https://code.visualstudio.com/docs/languages/java) has setup details.

## Controls

| Screen | Keyboard | Mouse |
| --- | --- | --- |
| Main menu | `1` Start, `2` Shop, `3` Settings, `4` Quit; arrows + Enter also work | Hover, then click |
| Region map | `1`–`5`, arrows + Enter | Hover highlights; click a region to play |
| Cleaning | WASD/arrows move; `E` or Space picks up/sorts; `Q` switches bag item (shown over the bag); `M` opens shared shop; `R` requests a restart; Esc opens Settings; `F11` or Alt+Enter toggles full screen | Click the gear for Settings or the speaker to mute game audio |
| Shop | `1`/`2` or arrows + Enter buy; Backspace closes; Esc opens Settings | Hover a cleaner card, then click to buy or click Back |
| Settings | Arrow keys + Enter; Esc backs out or closes Settings | In a stage: Controls, Audio, Exit to Map Select, Return Main Menu. From map selection: Controls, Audio, Return Main Menu. From main menu: Controls, Audio, Return |
| Audio | Select Edit, adjust with left/right, Save, then confirm Yes/No | Select Edit to drag the 0–100% stage-music slider; Save asks Yes/No. No discards. The speaker icon mutes all game sound. |
| Results | Arrows + Enter; `R` retries; Esc opens Settings | Hover and click a result button; MAP SELECT returns to stage choice |

The window can be resized down to 640×360 or maximized. Menus, map selection, and settings keep their standard size in the default window, shrink only when needed, and scale up moderately when maximized or in borderless full screen. Press `F11` or `Alt+Enter` to toggle borderless full screen. On the main menu, **Settings & Audio** opens Controls, Audio, and Return. Escape and the gear are reserved for in-game screens. Game music pauses while Settings is open.

## Progress and equipment

The top-right area shows an area-named Stage Completion Bar, a bold percentage, and the number of unsorted items left, including trash in the player's bag. Its fill eases toward the current progress and turns green at completion. Progress saves when you leave; pressing `R` asks for confirmation, and Yes restarts the stage, music, and stage progress. Player hearts are the only health system. The shared wallet and cleaner upgrades persist across maps. The cleaner cards have reserved image placeholders for the icons Anthony will provide. The Sun Grabber costs 250 coins. The combined Sun Grabber + Vacuum Pack costs about 1,204 coins, based on expected full-clear pickup, sorting, and completion rewards across the first three stages; it has a larger pickup range, faster movement, and a 10-item bag. No stage unlock is required to buy either upgrade.

The save file `save.properties` is created beside the game after progress changes. Keep it there to retain progress.

## Stages and audio

- Residential Street uses the supplied original level and player sprites.
- Town Marketplace uses the newest Level 3 layout and pixel-art stalls, with a weighted market-only trash catalogue and matching pixel icons. It has the Waste King, a five-minute timer, moving carts that can knock a carried item loose, and puddles that slow movement. Correct sorting fills the Market Stage Completion Bar; mistakes cost a heart and add litter. Market rewards follow the Level 3 build, and the existing shared shop remains in place.
- Coastal Beach uses the supplied bright beach image with lighthouse and umbrellas, 120 scattered beach items plus seaweed/driftwood in the water, and the supplied Natural Mystic track. Trees, huts, umbrellas, the pier, and other scenery block the player and are kept clear of trash. The four bins sit in an open sand area.
- The boat appears when the player reaches blue water. `W/S` throttle and `A/D` steer with gradual acceleration and turn rate.
- At 75% beach cleanup, a warning cinematic zooms to the sewage morphling as it rises in a water splash, then starts five trash waves. The boss shifts position and sprays water; touching it slows the player for six seconds. Clear all five waves within five minutes; it has no health bar.
- The beach soundtrack loops without the silent tail. The Music Volume slider reaches silence at 0%; the speaker button toggles master game mute.
- Public Park and City Center are playable in the same tile-art style and use the same sorting rules.

The original MP3 is in `assets/music/NaturalMystic.mp3`. `NaturalMystic.wav` is a PCM copy used by Java's built-in audio player, so no extra library is needed. Coastal litter uses the 24 items from the supplied resource pack reference; Town Marketplace loads its own weighted items from `data/trash/market.csv` and displays the market's item-specific icons. Animal poo is worth 1 coin.
