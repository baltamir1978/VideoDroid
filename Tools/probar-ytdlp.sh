#!/bin/sh
# Prueba los argumentos de yt-dlp (Arguments.kt) en este equipo, sin móvil: saca el yt-dlp que
# trae youtubedl-android, genera un vídeo de 5 s con audio, lo sirve por HTTP y corre RealYtdlpTest
# con el Python y el ffmpeg del sistema.
set -eu
cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'kill $server 2>/dev/null; rm -rf "$work"' EXIT

ver=$(sed -n 's/^ytdl = "\(.*\)"/\1/p' gradle/libs.versions.toml)
aar=$(find ~/.gradle/caches -name "library-$ver.aar" | head -1)
[ -n "$aar" ] || { echo "No encuentro library-$ver.aar en la caché de Gradle: compila antes"; exit 1; }
unzip -p "$aar" res/raw/ytdlp > "$work/yt-dlp"

mkdir "$work/www"
ffmpeg -loglevel error -f lavfi -i testsrc=size=640x360:rate=25 -f lavfi -i sine=frequency=440 \
       -t 5 -c:v libx264 -pix_fmt yuv420p -c:a aac -movflags +faststart "$work/www/Prueba de vídeo.mp4"
port=8765
python3 -m http.server -d "$work/www" $port >/dev/null 2>&1 &
server=$!
sleep 1

LINVIDEO_YTDLP="$work/yt-dlp" LINVIDEO_URL="http://127.0.0.1:$port/Prueba%20de%20v%C3%ADdeo.mp4" \
    ./gradlew testDebugUnitTest --tests '*RealYtdlpTest*' --rerun-tasks "$@"
