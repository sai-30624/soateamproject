#!/usr/bin/env bash
# Stops the services started by start-local.sh
cd "$(dirname "$0")/.."
[ -f logs/pids.txt ] || { echo "Nothing to stop."; exit 0; }
while read -r pid; do kill "$pid" 2>/dev/null && echo "stopped pid $pid"; done < logs/pids.txt
rm -f logs/pids.txt
