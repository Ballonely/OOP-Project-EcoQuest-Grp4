@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\ecoquest\*.java && java -cp out ecoquest.Main
