#!/bin/bash
# Ricompila il player (target Java 8) e rigenera TablutPlayer.jar
set -e
cd "$(dirname "${BASH_SOURCE[0]}")"
rm -rf build jarbuild && mkdir build jarbuild
javac --release 8 -nowarn -cp gson-2.2.2.jar -d build src/tablutai/*.java 2>/dev/null || javac -source 8 -target 8 -nowarn -cp gson-2.2.2.jar -d build src/tablutai/*.java
cp -r build/tablutai jarbuild/
(cd jarbuild && unzip -qo ../gson-2.2.2.jar -x 'META-INF/*')
jar cfe TablutPlayer.jar tablutai.Main -C jarbuild .
rm -rf build jarbuild
echo "OK: TablutPlayer.jar"
