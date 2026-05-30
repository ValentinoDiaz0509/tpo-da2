#!/bin/bash
# Runs automatically inside the LocalStack container once it is ready
# (mounted at /etc/localstack/init/ready.d). Creates the DynamoDB telemetry
# table the backend expects (aws.dynamodb.table-name = m9-telemetry-readings).
set -e

awslocal dynamodb create-table \
    --table-name m9-telemetry-readings \
    --attribute-definitions \
        AttributeName=patient_id,AttributeType=S \
        AttributeName=recorded_at,AttributeType=S \
    --key-schema \
        AttributeName=patient_id,KeyType=HASH \
        AttributeName=recorded_at,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST 2>/dev/null \
  || echo "Table m9-telemetry-readings already exists, skipping."

awslocal dynamodb update-time-to-live \
    --table-name m9-telemetry-readings \
    --time-to-live-specification "Enabled=true,AttributeName=expires_at" 2>/dev/null \
  || true

echo "LocalStack: DynamoDB table m9-telemetry-readings ready."
