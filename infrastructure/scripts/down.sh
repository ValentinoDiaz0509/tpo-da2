#!/usr/bin/env bash
# Tear the whole M9 backend DOWN using CDK.
# Removes: VPC, ALB, ECS, SQS, RDS, DynamoDB, ECR (images included), IAM, logs.
# Idle cost after this is ~$0.
#
# WARNING: DESTROYS the database and all telemetry. For a university project
# that's fine — DataSeeder repopulates demo data on the next ./up.sh.
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

if ! stack_exists; then
  echo "Stack '$STACK_NAME' is not deployed. Nothing to do. (Idle cost ~\$0.)"
  exit 0
fi

read -r -p "This will DESTROY stack '$STACK_NAME' and its database. Type 'yes' to confirm: " ans
if [[ "$ans" != "yes" ]]; then
  echo "Aborted."
  exit 1
fi

echo "==> Destroying stack '$STACK_NAME' ..."
(cd "$CDK_DIR" && npx cdk destroy "$STACK_NAME" --force --region "$REGION")

echo "DOWN. Everything removed. Idle cost ~\$0/mo."
