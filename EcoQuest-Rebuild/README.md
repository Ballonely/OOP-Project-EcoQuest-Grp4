# EcoQuest group project copy

This is the shared EcoQuest project with the teammate stage updates integrated into the current settings, menu, shared shop, and game runner. The original menu, five-region selector, player sprites, and shared progress/equipment save remain in place.

## Run it

1. Install a 64-bit JDK 17 or newer. VS Code by itself does not include Java. Check in a terminal with `java --version` and `javac --version`.
2. Install Microsoft's **Extension Pack for Java** in VS Code.
3. Open this `EcoQuest-Rebuild` folder in VS Code and press **F5**, or double-click `run.bat`. The project includes its MP3 decoder under `lib/`.

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
- Public Park uses Jaden's park layout, paths, pond, trees, benches, ten lamps, six moving LitterBots, and the Litter Beast. The beast appears after park cleanliness reaches 50%, adds litter, and is defeated at 80%; the park keeps its three-minute timer and 150-coin boss reward.
- Town Marketplace uses Jheremy's newest Level 3 layout, 16 pixel-art stalls, weighted market trash data, and the item's matching pixel icons. Its latest tuning is a six-minute timer, 34 starting items, a three-item bag, and a 40-item target. Moving carts can knock a carried item loose; puddles slow movement. Market mistakes cost a heart and add litter. The existing shared shop remains in place.
- Coastal Beach uses the supplied bright beach image with lighthouse, huts, umbrellas, pier, and scattered obstacles; 64 beach items plus 6 seaweed/driftwood pieces in the water; and the supplied Natural Mystic track. Scenery collision also keeps trash spawns away from props and bins. The four bins sit in open sand.
- The boat appears when the player reaches blue water. `W/S` throttle and `A/D` steer with gradual acceleration and turn rate.
- The beach boss starts only after all 70 regular pieces are sorted. The stage bar holds at 99% during the fight; boss-wave litter uses a separate counter and does not change beach completion. Defeating the boss completes the final 1%. Five waves scatter 10, 13, 16, 19, and 22 pieces across the island and water, with five minutes to finish. Every 5.5 seconds the boss launches a spaced, alternating barrage of at least eight attack circles; bright red circles and a MOVE warning give 1.15 seconds to escape. The muddy puddles last three seconds. A direct hit stuns and mud-slows the player, but deals no health damage. The boss shifts slightly and slows the player for six seconds on contact.
- Beach audio switches from Natural Mystic to the supplied red-alert cue during the warning, then to the supplied battle track after the cinematic. It returns to the beach track after the boss ends. Music pauses in Settings; the 0–100% slider controls stage music and the speaker button mutes game audio.
- Residential Street and City Center continue using the same shared sorting rules and UI.

The supplied tracks are in `assets/music/`. MP3 decoding uses JLayer 1.0.1, bundled in `lib/` with its LGPL-2.1 license; VS Code is configured to include that jar. Coastal litter uses the resource-pack items; Town Marketplace loads its own weighted items from `data/trash/market.csv` and uses the teammate icon atlas. Animal poo is worth 1 coin.
