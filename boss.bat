@echo off
REM Developer shortcut: compiles and launches EcoQuest straight into a level's boss fight as the
REM built-in developer account (nothing saved), with auto-respawn on.
REM   boss.bat          Level 1 street - Trash Monster
REM   boss.bat park     Level 2 park   - Litter Beast      (or: boss.bat 2)
REM   boss.bat market   Level 3 market - Waste King        (or: boss.bat 3)
REM   boss.bat beach    Level 4 beach  - Sewage Morphling  (or: boss.bat 4)
REM   boss.bat city     Level 5 city   - Trash Monster     (or: boss.bat 5)
REM In game: F1 = developer key guide, F8 = auto-respawn on/off, F9 = restart at the boss, R = restart.
call "%~dp0run.bat" --boss %1
