#!/usr/bin/env bash
# Runs the full Play Store screenshot pipeline for Constanza: both languages, all three formats,
# then verifies the result. See README.md for prerequisites (debug + debug-androidTest installed,
# permissions granted, emulator booted).
set -euo pipefail
cd "$(dirname "$0")"

SERIAL="${1:?usage: todos.sh <emulator-serial>}"

python3 tanda.py "$SERIAL" es
python3 tanda.py "$SERIAL" en
python3 revisar.py
