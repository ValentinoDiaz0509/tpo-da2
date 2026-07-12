#!/usr/bin/env bash
# Bring the whole M9 backend UP using CDK.
#
#   ./up.sh [DESIRED_COUNT]    (default 1)
#
# First run: deploys the stack with 0 tasks (ECR is empty), builds & pushes the
# image, then redeploys with the requested task count. Subsequent runs: rebuild/
# push the image and force a new ECS deployment. Idempotent.
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

DESIRED="${1:-${DESIRED_COUNT:-1}}"

if stack_exists; then
  echo "==> Stack '$STACK_NAME' already exists — will rebuild image and roll the service."
else
  echo "==> First deploy: creating stack with desiredCount=0 (ECR is empty)..."
  deploy_stack 0
fi

REPO_URI="$(get_output EcrRepositoryUri)"
REGISTRY="${REPO_URI%%/*}"

echo "==> Building and pushing image to $REPO_URI:latest ..."
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"
docker build -t "$REPO_URI:latest" "$BACKEND_DIR"
docker push "$REPO_URI:latest"

echo "==> Scaling service to desiredCount=$DESIRED ..."
deploy_stack "$DESIRED"

echo "==> Rolling ECS service onto the new image ..."
aws ecs update-service \
  --cluster "$(get_output ClusterName)" \
  --service "$(get_output ServiceName)" \
  --force-new-deployment --region "$REGION" >/dev/null

echo "==> Waiting for the service to stabilize..."
aws ecs wait services-stable \
  --cluster "$(get_output ClusterName)" \
  --services "$(get_output ServiceName)" --region "$REGION"

echo ""
echo "UP. Backend: $(get_output LoadBalancerUrl)/api/v1"
echo "    Swagger:  $(get_output LoadBalancerUrl)/api/v1/swagger-ui.html"
