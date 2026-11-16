#!/usr/bin/env bash
# Save a screenshot of the TV under shots/<date>/<time>.png.
#
# Usage: TV_HOST=192.168.1.50 tools/shot.sh [KEY ...] [--wait SECONDS]
#
# A run at 10:23:45 on 16 Nov 2026 writes shots/2026-11-16/10-23-45.png. A second shot in that
# same second is shots/2026-11-16/10-23-45-2.png. Keys are the names tv.py already takes
# (HOME, DPAD_RIGHT). The exit status is tv.py's: 0 the picture was saved, 1 the capture failed,
# 2 the command was refused before the TV was asked.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 tools/shot.sh}"

PY=$ROOT/tools/venv/bin/python
[ -x "$PY" ] || PY=$ROOT/tools/venv/Scripts/python.exe
if [ ! -x "$PY" ]; then
  echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2
  exit 2
fi

day=$(date +%Y-%m-%d)
clock=$(date +%H-%M-%S)
dir=$ROOT/shots/$day
mkdir -p "$dir"
file=$dir/$clock.png
n=2
while [ -e "$file" ]; do
  file=$dir/${clock}-$n.png
  n=$((n + 1))
done

"$PY" -I "$ROOT/tools/tv.py" --screenshot "$file" "$@"
