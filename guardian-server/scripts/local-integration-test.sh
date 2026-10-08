#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

GRADLE_BIN="${GRADLE_BIN:-gradle}"
GUARDIAN_PORT="${GUARDIAN_PORT:-18080}"
MOCK_GEMINI_PORT="${MOCK_GEMINI_PORT:-18787}"
TMP_DIR="$(mktemp -d)"
MOCK_PID=""
GUARDIAN_PID=""

cleanup() {
  local exit_code=$?
  if [[ "$exit_code" -ne 0 ]]; then
    echo "--- local Guardian log ---" >&2
    cat "$TMP_DIR/guardian.log" 2>/dev/null >&2 || true
    echo "--- mock Gemini log ---" >&2
    cat "$TMP_DIR/mock-gemini.log" 2>/dev/null >&2 || true
  fi
  for pid in "$GUARDIAN_PID" "$MOCK_PID"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  done
  rm -rf "$TMP_DIR"
  return "$exit_code"
}
trap cleanup EXIT

python3 guardian-server/scripts/test_mock_gemini.py
"$GRADLE_BIN" --no-daemon --console=plain :guardian-server:installDist

python3 guardian-server/scripts/mock_gemini.py --port "$MOCK_GEMINI_PORT" >"$TMP_DIR/mock-gemini.log" 2>&1 &
MOCK_PID=$!

wait_for_health() {
  local url="$1"
  local pid="$2"
  local label="$3"
  local log="$4"
  for attempt in {1..60}; do
    if curl --fail --silent "$url" >/dev/null; then
      echo "$label: healthy"
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "$label exited before becoming healthy:" >&2
      cat "$log" >&2
      return 1
    fi
    sleep 1
  done
  echo "$label did not become healthy:" >&2
  cat "$log" >&2
  return 1
}

wait_for_health "http://127.0.0.1:${MOCK_GEMINI_PORT}/health" "$MOCK_PID" "mock Gemini" "$TMP_DIR/mock-gemini.log"

GEMINI_API_KEY="local-test-key" \
GEMINI_API_URL="http://127.0.0.1:${MOCK_GEMINI_PORT}/v1beta/interactions" \
PORT="$GUARDIAN_PORT" \
  guardian-server/build/install/guardian-server/bin/guardian-server >"$TMP_DIR/guardian.log" 2>&1 &
GUARDIAN_PID=$!

BASE_URL="http://127.0.0.1:${GUARDIAN_PORT}"
wait_for_health "$BASE_URL/health" "$GUARDIAN_PID" "local Guardian" "$TMP_DIR/guardian.log"
bash guardian-server/scripts/integration-test.sh "$BASE_URL"
echo "local Guardian/Gemini HTTP integration: passed"
