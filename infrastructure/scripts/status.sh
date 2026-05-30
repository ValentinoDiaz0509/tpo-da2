#!/usr/bin/env bash
# Show whether the M9 backend is up, how many tasks are running, and a rough
# cost reminder.
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

if ! stack_exists; then
  echo "Stack '$STACK_NAME': NOT DEPLOYED"
  echo "Idle cost: ~\$0/mo"
  exit 0
fi

STATUS="$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" \
  --query 'Stacks[0].StackStatus' --output text)"
echo "Stack '$STACK_NAME': $STATUS"

CLUSTER="$(get_output ClusterName)"
SERVICE="$(get_output ServiceName)"
if [[ -n "$CLUSTER" && "$CLUSTER" != "None" ]]; then
  read -r DESIRED RUNNING <<<"$(aws ecs describe-services \
    --cluster "$CLUSTER" --services "$SERVICE" --region "$REGION" \
    --query 'services[0].[desiredCount,runningCount]' --output text)"
  echo "ECS service '$SERVICE': running ${RUNNING:-?} / desired ${DESIRED:-?}"
  echo "URL: $(get_output LoadBalancerUrl)/api/v1"
  echo ""
  echo "Approx cost WHILE UP:"
  echo "  ~\$30/mo always-on floor (ALB + RDS), regardless of task count"
  echo "  + ~\$11/mo per running Fargate task (0.25 vCPU / 1 GB)"
  echo "Run ./down.sh when you're done to drop to ~\$0."
fi
