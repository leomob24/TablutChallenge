#!/bin/bash
# Usage: runmyplayer.sh ROLE TIMEOUT_SECONDS SERVER_IP   (e.g. ./runmyplayer.sh WHITE 60 192.168.20.101)
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -Xmx2g -jar "$DIR/TablutPlayer.jar" "$1" "$2" "$3"
