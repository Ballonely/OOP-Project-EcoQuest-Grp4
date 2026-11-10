#!/bin/sh
# Compile and run Eco Quest Level 3 (Market). Needs JDK 17+.
cd "$(dirname "$0")" || exit 1
mkdir -p out
javac -encoding UTF-8 -d out src/ecoquest/*.java && java -cp out ecoquest.Main
