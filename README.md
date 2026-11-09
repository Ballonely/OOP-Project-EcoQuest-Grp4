# OOP-Project-EcoQuest-Grp4
A project for OOP that would be a game

<img width="1007" height="1296" alt="image" src="https://github.com/user-attachments/assets/a1391c1d-b23d-450c-868a-9b316da08447" />

## Group Janemba

### Grp Members:
- Jaden Charles Dacanay
- Benjiro Bahinting
- Jheremy Lobitaña
- Anthony Senining

---

## Eco Quest 

Eco Quest is an educational game where players take on the role of a city cleaner whose goal is to reduce pollution and keep the city clean. Players explore different areas, collect scattered garbage, and properly sort waste into categories such as recyclable, biodegradable, and non-biodegradable. As the player cleans more areas, the city's cleanliness and environmental score improve. The game aims to promote proper waste disposal, recycling, and environmental awareness while providing an interactive and enjoyable gaming experience.

### Basic Gameplay
1. **Start in a polluted area**
   The player controls a character walking around the city.
   Garbage is scattered around streets, parks, and other locations.
   There can be obstacles like cars, puddles, or blocked paths.

2. **Pick up garbage**
   The player approaches garbage and presses a button to collect it.
   Different garbage has different types:
   - Biodegradable
   - Recyclable
   - Non-recyclable
   - Hazardous

3. **Sort the garbage**
   The player must bring the garbage to the correct trash bin.
   Correct sorting gives points.
   Incorrect sorting loses points or health.

4. **Clean different areas**
   Levels include:
   - Level 1: Residential Street
   - Level 2: Public Park
   - Level 3: Market
   - Level 4: Beach
   - Level 5: City Center

---

## Java OOP Prototype Implementation (Unfinished Draft)

A fully object-oriented 2D arcade game built with pure Java (Swing/AWT, no external dependencies), reverse-engineered from the supplied reference UI mockup.

### Requirements
- JDK 17 or newer (uses Java switch expressions)

### Build & Run
You can run the game directly with the provided batch file:
```cmd
run.bat
```

Or from the command line:
```bash
mkdir -p bin
javac -d bin src/com/ecoquest/Main.java src/com/ecoquest/core/*.java src/com/ecoquest/entities/*.java src/com/ecoquest/level1/*.java src/com/ecoquest/ui/*.java src/com/ecoquest/util/*.java
java -cp bin com.ecoquest.Main
```

### Controls
- **Move:** Arrow keys or WASD
- **Level 1:** E picks up nearby litter or sorts the selected item into a nearby bin; Q cycles the bag; R restarts
- **Menu navigate:** Up/Down, or mouse hover + click
- **Select / Start:** Space or Enter
- **Level selection:** Left/Right or Up/Down, then Space/Enter to start; Esc returns to the menu
- **Pause / Resume:** P
- **Back to menu (from Pause):** Esc
- **Return to menu (from Game Over):** Space

### Architecture

```
com.ecoquest
 ├─ Main.java                  Application entry point (JFrame bootstrap)
 ├─ core/
 │   ├─ Game.java              Game controller: JPanel + game loop thread,
 │   │                         state machine, decoupled update()/render()
 │   ├─ GameState.java         MAIN_MENU / LEVEL_SELECT / PLAYING / PAUSED / GAME_OVER
 │   └─ InputHandler.java      Polled keyboard + mouse state (no listener lag)
 ├─ entities/
 │   ├─ GameObject.java        Abstract base: x, y, w, h, velocity, bounds
 │   ├─ Player.java            Player-controlled hero: movement, HP, score
 │   ├─ Enemy.java             Roaming "pollution cloud" hazard
 │   └─ Collectible.java       "Eco token" pickup (score + cleanliness)
 ├─ level1/
 │   └─ Level1Residential.java Residential streets, litter sorting, and the Trash Monster NPC at 80% city health
 └─ ui/
     ├─ MenuManager.java       Main menu screen (title, tagline, buttons,
     │                         top/bottom HUD strips) — matches reference art
     ├─ LevelSelectManager.java Level selection screen and stage navigation
     └─ UIManager.java         In-game HUD, Pause overlay, Game Over overlay
```
