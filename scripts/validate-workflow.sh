#!/usr/bin/env bash
# validate-workflow.sh — GitHub Actions workflow syntax validator (T015, T032)
#
# Usage: bash scripts/validate-workflow.sh <workflow-file>
#   e.g. bash scripts/validate-workflow.sh .github/workflows/pr.yml
#        bash scripts/validate-workflow.sh .github/workflows/release.yml
#
# Uses actionlint when available; falls back to python3 yaml.safe_load.
#
# Exit codes:
#   0 — workflow is valid
#   1 — syntax errors found or no validator available

set -euo pipefail

WORKFLOW="${1:-}"

validate_args() {
    if [[ -z "$WORKFLOW" ]]; then
        echo "Usage: $0 <workflow-file>" >&2
        exit 1
    fi
    if [[ ! -f "$WORKFLOW" ]]; then
        echo "❌ File not found: $WORKFLOW" >&2
        exit 1
    fi
}

validate_with_actionlint() {
    command -v actionlint &>/dev/null || return 1
    echo "Using actionlint..."
    actionlint "$WORKFLOW"
}

validate_with_python_yaml() {
    command -v python3 &>/dev/null || return 1
    echo "actionlint not found — falling back to yaml.safe_load check..."
    python3 -c "import yaml, sys; yaml.safe_load(open('$WORKFLOW'))" \
        && echo "✅ YAML structure valid"
}

main() {
    validate_args
    echo "=== Workflow Validator: $WORKFLOW ==="
    validate_with_actionlint && exit 0
    validate_with_python_yaml && exit 0
    echo "⚠️  No validator available. Install: brew install actionlint" >&2
    exit 1
}

main "$@"
