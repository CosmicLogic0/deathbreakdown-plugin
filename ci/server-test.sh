#!/usr/bin/env bash
# Starts a Minecraft server, waits for "Done (", stops it, and checks the log.
# Usage: server-test.sh <server-dir> <expected-log-regex> -- <start command...>
# Copied from CosmicLogic0/logiclib ci/server-test.sh. CHECK_NAMES (regex) = mods whose log lines must have no errors.
# Added: CONSOLE_CMDS (lines typed into the console after start) and EXPECT_AFTER (regex that must then appear).
set -u
DIR="$1"; EXPECT="$2"; shift 3
cd "$DIR"
echo "eula=true" > eula.txt
rm -f console.in; mkfifo console.in
sleep infinity > console.in &
KEEP=$!
"$@" < console.in > server.log 2>&1 &
PID=$!
ok=0
for i in $(seq 1 900); do
  if grep -q 'Done (' server.log; then ok=1; break; fi
  if ! kill -0 "$PID" 2>/dev/null; then break; fi
  sleep 1
done
sleep 5
if [ -n "${CONSOLE_CMDS:-}" ]; then
  printf '%s\n' "$CONSOLE_CMDS" > console.in
  sleep 8
fi
echo "stop" > console.in
for i in $(seq 1 120); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
kill "$PID" 2>/dev/null || true
kill "$KEEP" 2>/dev/null || true
echo "================ last 80 log lines ================"
tail -n 80 server.log
echo "==================================================="
if [ "$ok" != "1" ]; then echo "::error::Server did not finish starting"; exit 1; fi
if ! grep -Eq "$EXPECT" server.log; then echo "::error::Expected log line not found: $EXPECT"; exit 1; fi
CHECK_NAMES="${CHECK_NAMES:-logiclib|deathbreakdown|death breakdown}"
if grep -Ei "$CHECK_NAMES" server.log | grep -Eiq "exception|error|could not load|failed"; then
  echo "::error::Mod problem in the log:"; grep -Ei "$CHECK_NAMES" server.log | grep -Ei "exception|error|could not load|failed"; exit 1
fi
if [ -n "${EXPECT_AFTER:-}" ] && ! grep -Eq "$EXPECT_AFTER" server.log; then echo "::error::Expected after console commands: $EXPECT_AFTER"; exit 1; fi
echo "PASS: server started, mods loaded with no errors."
