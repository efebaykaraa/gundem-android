#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
MODEL=${1:?GGUF model dosyasının tam yolunu verin}
mkdir -p "$ROOT/build/host-smoke-source"
cp "$ROOT/docs/native-smoke.CMakeLists.txt" "$ROOT/build/host-smoke-source/CMakeLists.txt"
cmake -S "$ROOT/build/host-smoke-source" -B "$ROOT/build/host-smoke" -DGUNDEM_ROOT="$ROOT" -DCMAKE_BUILD_TYPE=Release
cmake --build "$ROOT/build/host-smoke" --target gundem-smoke -j 4
"$ROOT/build/host-smoke/gundem-smoke" "$MODEL" "$ROOT/app/src/main/assets/prompts"
