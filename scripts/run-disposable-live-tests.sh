#!/usr/bin/env bash
set -euo pipefail

# This wrapper deliberately has no default server. Live tests must opt in to a disposable target,
# use a unique resource prefix, and provide an idempotent cleanup executable.
: "${ARCANE_E2E_CONFIRM:?Set ARCANE_E2E_CONFIRM=disposable}"
: "${ARCANE_E2E_BASE_URL:?Set the disposable Arcane base URL}"
: "${ARCANE_E2E_USERNAME:?Set the disposable Arcane username}"
: "${ARCANE_E2E_PASSWORD:?Set the disposable Arcane password}"
: "${ARCANE_E2E_RUN_ID:?Set a unique lowercase run ID}"
: "${ARCANE_E2E_CLEANUP:?Set an absolute path to an executable cleanup callback}"

if [[ "$ARCANE_E2E_CONFIRM" != "disposable" ]]; then
    echo "Refusing live tests without ARCANE_E2E_CONFIRM=disposable." >&2
    exit 2
fi

case "$ARCANE_E2E_BASE_URL" in
    https://127.0.0.1:43553|https://localhost:43553|https://10.0.2.2:43553) ;;
    *)
        echo "Refusing unapproved live-test target: $ARCANE_E2E_BASE_URL" >&2
        exit 2
        ;;
esac

if [[ ! "$ARCANE_E2E_RUN_ID" =~ ^[a-z0-9][a-z0-9-]{5,47}$ ]]; then
    echo "ARCANE_E2E_RUN_ID must be a unique 6-48 character lowercase identifier." >&2
    exit 2
fi
ARCANE_E2E_TIMEOUT_SECONDS="${ARCANE_E2E_TIMEOUT_SECONDS:-900}"
if [[ ! "$ARCANE_E2E_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] ||
    (( ARCANE_E2E_TIMEOUT_SECONDS < 30 || ARCANE_E2E_TIMEOUT_SECONDS > 3600 )); then
    echo "ARCANE_E2E_TIMEOUT_SECONDS must be between 30 and 3600." >&2
    exit 2
fi
if [[ "$ARCANE_E2E_CLEANUP" != /* || ! -x "$ARCANE_E2E_CLEANUP" ]]; then
    echo "ARCANE_E2E_CLEANUP must be an absolute executable path." >&2
    exit 2
fi
if [[ "$#" -eq 0 ]]; then
    echo "Pass the bounded live-test command after --." >&2
    exit 2
fi
if [[ "$1" == "--" ]]; then
    shift
fi
if [[ "$#" -eq 0 ]]; then
    echo "No live-test command supplied." >&2
    exit 2
fi

export ARCANE_E2E_RESOURCE_PREFIX="par-live-${ARCANE_E2E_RUN_ID}"

cleanup() {
    local test_status=$?
    local cleanup_status=0
    trap - EXIT INT TERM
    "$ARCANE_E2E_CLEANUP" || cleanup_status=$?
    if [[ $cleanup_status -ne 0 ]]; then
        echo "Disposable live-test cleanup failed." >&2
        exit "$cleanup_status"
    fi
    exit "$test_status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

curl_args=(--fail --silent --show-error --max-time 10)
if [[ -n "${ARCANE_E2E_CA_CERT:-}" ]]; then
    if [[ ! -f "$ARCANE_E2E_CA_CERT" ]]; then
        echo "ARCANE_E2E_CA_CERT does not name a readable certificate." >&2
        exit 2
    fi
    curl_args+=(--cacert "$ARCANE_E2E_CA_CERT")
fi
curl "${curl_args[@]}" "$ARCANE_E2E_BASE_URL/api/health" >/dev/null

timeout --signal=TERM --kill-after=30s "${ARCANE_E2E_TIMEOUT_SECONDS}s" "$@"
