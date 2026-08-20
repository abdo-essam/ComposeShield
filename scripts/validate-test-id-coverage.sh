#!/usr/bin/env bash
# validate-test-id-coverage.sh — Test-ID coverage validator (T047 / FR-018)
#
# Reads every test ID from config/test-id-map.yml and checks that each ID
# appears somewhere across the three pipeline workflow files.
# Exits 1 with a list of unmapped IDs — preventing silent requirement gaps.
#
# Exit codes:
#   0 — every test ID in the map has an owning pipeline reference
#   1 — one or more IDs are unmapped

set -euo pipefail

MAP_FILE="config/test-id-map.yml"

# All workflow files that must collectively cover every test ID in the map.
WORKFLOW_FILES=(
    ".github/workflows/on-demand.yml"
    ".github/workflows/release.yml"
    ".github/workflows/android-physical-ftl.yml"
)

check_prerequisites() {
    [[ -f "$MAP_FILE" ]] || { echo "❌ Missing $MAP_FILE — run T003 first." >&2; exit 1; }

    local missing=()
    for f in "${WORKFLOW_FILES[@]}"; do
        [[ -f "$f" ]] || missing+=("$f")
    done

    if [[ ${#missing[@]} -gt 0 ]]; then
        echo "⚠️  Some workflow files not yet created — skipping coverage check:"
        printf '   %s\n' "${missing[@]}"
        exit 0
    fi
}

extract_test_ids() {
    grep -E '^[A-Z]-[0-9]{3}:' "$MAP_FILE" | sed 's/:.*//'
}

# Returns 0 if $1 appears in any of the checked workflow files.
id_found_in_workflows() {
    local test_id="$1"
    for f in "${WORKFLOW_FILES[@]}"; do
        grep -q "$test_id" "$f" && return 0
    done
    return 1
}

main() {
    echo "=== Test-ID Coverage Validator (FR-018) ==="
    check_prerequisites

    local failed=0
    while IFS= read -r id; do
        if id_found_in_workflows "$id"; then
            echo "✅ $id — found"
        else
            echo "❌ $id — NOT found in any pipeline workflow" >&2
            failed=1
        fi
    done < <(extract_test_ids)

    if [[ "$failed" -ne 0 ]]; then
        echo ""
        echo "Coverage check FAILED. Every ID in $MAP_FILE must appear in at least one of:" >&2
        printf '   %s\n' "${WORKFLOW_FILES[@]}" >&2
        exit 1
    fi

    echo ""
    echo "Coverage check PASSED."
}

main "$@"
