#!/usr/bin/env bash
# Runs the packaged jar against a real database and a stand-in for OpenRouter,
# checks each request path end to end, and reports how long stored decks take.
#
# Usage: scripts/smoke-test.sh target/flashquiz-ai-0.0.1-SNAPSHOT.jar
#
# The database comes from the usual DB_HOST, DB_PORT, DB_NAME, DB_USER and
# DB_PASSWORD variables. Point them at a disposable database: the test writes to it.
set -euo pipefail

JAR=${1:?usage: smoke-test.sh <application jar>}
APP_PORT=${APP_PORT:-18080}
STUB_PORT=${STUB_PORT:-18081}
HIT_REQUESTS=${HIT_REQUESTS:-50}
READ_TIMEOUT_SECONDS=2

APP=http://127.0.0.1:$APP_PORT
STUB=http://127.0.0.1:$STUB_PORT
WORK=$(mktemp -d)
LOG=$WORK/app.log
BODY=$WORK/body.html
PYTHON=$(command -v python3 || command -v python)

cleanup() {
    kill "${APP_PID:-}" "${STUB_PID:-}" 2>/dev/null || true
    rm -rf "$WORK"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $*"
    echo "--- last application log lines"
    tail -n 40 "$LOG" || true
    exit 1
}

# Posts a topic. Leaves the page in $BODY and prints "<status> <seconds>".
generate() {
    curl -sS -o "$BODY" -w '%{http_code} %{time_total}' --data-urlencode "topicText=$1" "$APP/generate"
}

model_calls() {
    curl -fsS "$STUB/" | tr -dc '0-9'
}

expect() {
    local label=$1 want_status=$2 want_text=$3 got_status=$4
    [ "$got_status" = "$want_status" ] || fail "$label: expected HTTP $want_status, got $got_status"
    grep -q "$want_text" "$BODY" || fail "$label: page does not contain '$want_text'"
}

"$PYTHON" "$(dirname "$0")/fake_openrouter.py" "$STUB_PORT" &
STUB_PID=$!

PORT=$APP_PORT \
OPENROUTER_API_KEY=smoke-test-key \
OPENROUTER_BASE_URL=$STUB \
OPENROUTER_READ_TIMEOUT=${READ_TIMEOUT_SECONDS}s \
    java -jar "$JAR" >"$LOG" 2>&1 &
APP_PID=$!

for _ in $(seq 90); do
    curl -fsS -o /dev/null "$APP/" 2>/dev/null && break
    kill -0 "$APP_PID" 2>/dev/null || fail "application exited during startup"
    sleep 1
done
curl -fsS -o /dev/null "$APP/" || fail "application did not answer on $APP within 90 s"

TOPIC="smoke test $(date +%s)"

# 1. New topic: the model is called once and the deck is rendered.
read -r status miss_seconds <<<"$(generate "$TOPIC")"
expect "new topic" 200 "Question 15?" "$status"
grep -q "saved deck" "$BODY" && fail "new topic: served as a saved deck"
[ "$(model_calls)" = 1 ] || fail "new topic: expected 1 model call, saw $(model_calls)"

# 2. Same topic again, with different case and spacing: answered from the database.
times=$WORK/times
for _ in $(seq "$HIT_REQUESTS"); do
    read -r status seconds <<<"$(generate "  ${TOPIC^^}  ")"
    expect "repeat topic" 200 "saved deck" "$status"
    echo "$seconds" >>"$times"
done
[ "$(model_calls)" = 1 ] || fail "repeat topic: model was called again ($(model_calls) calls)"

# 3. Model returns an error: 502 and the form, not an empty result page.
read -r status _ <<<"$(generate "smoke-error $TOPIC")"
expect "upstream error" 502 "unavailable right now" "$status"

# 4. Model is slower than the read timeout: 504 soon after the timeout, not after the model finishes.
read -r status slow_seconds <<<"$(generate "smoke-slow $TOPIC")"
expect "slow upstream" 504 "took too long" "$status"
awk -v s="$slow_seconds" -v limit="$((READ_TIMEOUT_SECONDS + 2))" 'BEGIN { exit !(s < limit) }' \
    || fail "slow upstream: took ${slow_seconds}s, read timeout is ${READ_TIMEOUT_SECONDS}s"

report=$(sort -n "$times" | awk -v miss="$miss_seconds" -v slow="$slow_seconds" -v timeout="$READ_TIMEOUT_SECONDS" '
    { t[NR] = $1 * 1000 }
    END {
        p50 = t[int((NR + 1) / 2)]
        p95_index = int(NR * 0.95); if (p95_index < NR * 0.95) p95_index++
        printf "| Path | Result |\n|---|---|\n"
        printf "| Saved deck, %d requests | p50 %.0f ms, p95 %.0f ms, max %.0f ms |\n", NR, p50, t[p95_index], t[NR]
        printf "| New topic, model stand-in replying instantly | %.0f ms |\n", miss * 1000
        printf "| Model slower than the %d s read timeout | 504 after %.1f s |\n", timeout, slow
    }')

echo "Smoke test passed."
echo "$report"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
        echo "### Smoke test on PostgreSQL"
        echo
        echo "$report"
        echo
        echo "Times are whole HTTP requests measured with curl on the CI runner."
        echo "The new-topic figure excludes the real model, which adds its own seconds."
    } >>"$GITHUB_STEP_SUMMARY"
fi
