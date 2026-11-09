@echo off
setlocal
cd /d "%~dp0"

REM Check if java is in PATH or look in local JDK install
where java >nul 2>&1
if errorlevel 1 (
    if exist "%LOCALAPPDATA%\Programs\jdk-21\bin\java.exe" (
        set "PATH=%LOCALAPPDATA%\Programs\jdk-21\bin;%PATH%"
    ) else (
        echo Error: Java was not found in PATH or in %LOCALAPPDATA%\Programs\jdk-21\bin.
        echo Please ensure Java 17+ is installed.
        pause
        exit /b 1
    )
)

REM Compile on every launch so changes to existing source files are applied
echo Compiling EcoQuest...
if not exist "bin" mkdir bin
javac -d bin src\com\ecoquest\Main.java src\com\ecoquest\core\*.java src\com\ecoquest\entities\*.java src\com\ecoquest\level1\*.java src\com\ecoquest\ui\*.java src\com\ecoquest\util\*.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)

if exist "assets" (
    if not exist "bin\assets" mkdir "bin\assets"
    xcopy /y /d /q "assets\*" "bin\assets\" >nul 2>&1
)

echo Starting EcoQuest...
start javaw -cp bin com.ecoquest.Main
exit /b 0
