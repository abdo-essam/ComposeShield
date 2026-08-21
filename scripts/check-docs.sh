#!/usr/bin/env bash
# check-docs.sh — Documentation gate (T007)
#
# Verifies that mandatory documentation files exist and are non-empty.
# Called from the docs-gate job in release.yml.
#
# Exit codes:
#   0 — all required docs present and non-empty
#   1 — one or more docs missing or empty (CI job will fail)
#
# Constitution Principle X: functions ≤ 40 lines, files ≤ 300 lines.

set -euo pipefail

REQUIRED_DOCS=(
  "docs/support-matrix.md"
  "docs/security-limitations.md"
)

FAILED=0

check_doc() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "❌ MISSING: $path" >&2
    FAILED=1
    return
  fi
  # wc -c counts bytes; a non-empty file has > 0 bytes
  local size
  size=$(wc -c < "$path" | tr -d ' ')
  if [[ "$size" -eq 0 ]]; then
    echo "❌ EMPTY: $path" >&2
    FAILED=1
    return
  fi
  echo "✅ OK: $path (${size} bytes)"
}

main() {
  echo "=== Documentation Gate ==="
  for doc in "${REQUIRED_DOCS[@]}"; do
    check_doc "$doc"
  done

  if [[ "$FAILED" -ne 0 ]]; then
    echo ""
    echo "Documentation gate FAILED. Add or populate the missing files above." >&2
    exit 1
  fi

  echo ""
  echo "Documentation gate PASSED. All required docs present and non-empty."
}

main "$@"
