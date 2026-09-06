#!/usr/bin/env bash
set -euo pipefail

script_path="${1:-acceptance_test.sh}"

[[ -f "$script_path" ]] || {
  printf 'Missing acceptance script: %s\n' "$script_path" >&2
  exit 1
}

grep -Fq 'PYTHON_BIN="$(command -v python3 || command -v python || true)"' "$script_path" || {
  printf 'Acceptance script must prefer python3 and fall back to python.\n' >&2
  exit 1
}

awk '
  /command -v python/ { next }
  /(^|[[:space:];|])python([[:space:]]|$)/ {
    print "Direct python invocation is not portable: " $0 > "/dev/stderr"
    failed = 1
  }
  END { exit failed }
' "$script_path"
