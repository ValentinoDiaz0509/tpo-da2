#!/usr/bin/env bash
# Shared config + helpers for M9 backend CDK stack scripts.
# Override defaults via env vars, e.g. STACK_NAME=M9BackendDev ./up.sh
set -euo pipefail

STACK_NAME="${STACK_NAME:-M9Backend}"
REGION="${AWS_REGION:-us-east-1}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CDK_DIR="$REPO_ROOT/infrastructure/cdk"
BACKEND_DIR="$REPO_ROOT/backend"

stack_exists() {
  aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" \
    >/dev/null 2>&1
}

# get_output <OutputKey>
get_output() {
  aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

# deploy_stack <desiredCount>  — CDK deploy with an optional context override
deploy_stack() {
  local desired="${1:-1}"
  (cd "$CDK_DIR" && npx cdk deploy "$STACK_NAME" \
    --context "desiredCount=$desired" \
    --require-approval never \
    --region "$REGION")
}
