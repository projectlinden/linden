#!/usr/bin/env bash
# Runs redact.lua against fake log lines and checks that no personal data survives.
# Usage (from the repo root): monitoring/fluent-bit/test/run-redact-test.sh
set -euo pipefail

IMAGE="fluent/fluent-bit:5.2.0"   # keep in sync with docker-compose.monitoring.yml
DIR="$(cd "$(dirname "$0")/.." && pwd)"

output="$(docker run --rm -v "$DIR":/fluent-bit/etc:ro --entrypoint /fluent-bit/bin/fluent-bit \
  "$IMAGE" -c /fluent-bit/etc/test/redact-test.yaml 2>&1)"

failures=0
# grep -F: compare plain text – "." and "[...]" are not regular expressions here
must_not_contain() {
  if grep -qF -- "$1" <<<"$output"; then echo "FAIL: output contains '$1'"; failures=$((failures + 1)); fi
}
must_contain() {
  if ! grep -qF -- "$1" <<<"$output"; then echo "FAIL: output is missing '$1'"; failures=$((failures + 1)); fi
}

# Personal data must be gone
must_not_contain "test.person@example.com"
must_not_contain "username="
must_not_contain "ipAddress="
must_not_contain "code_id="
must_not_contain "172.19.0.1"
must_not_contain "STATEMENT:"
must_not_contain "DETAIL:"

# Useful information must stay
must_contain "LOGIN_ERROR"
must_contain "user_not_found"
must_contain "does not exist"
must_contain "Thread@1a2b3c"
must_contain "[email removed]"

if [ "$failures" -gt 0 ]; then
  echo "$output"
  echo "redact test: $failures check(s) failed"
  exit 1
fi
echo "redact test: all checks passed"
