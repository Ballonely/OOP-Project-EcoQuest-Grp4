@echo off
setlocal
cd /d "%~dp0"

where javac >nul 2>&1
if errorlevel 1 (
  echo JDK 17 or newer is required. Install a JDK, then reopen this window.
  pause
  exit /b 1
)

if not exist "bin" mkdir "bin"
javac -encoding UTF-8 -d bin src\com\ecoquest\Main.java src\com\ecoquest\core\*.java src\com\ecoquest\entities\*.java src\com\ecoquest\level1\*.java src\com\ecoquest\ui\*.java src\com\ecoquest\util\*.java src\ecoquest\*.java
if errorlevel 1 (
  echo Build failed. Check the source and JDK version.
  pause
  exit /b 1
)

java -cp bin com.ecoquest.Main
