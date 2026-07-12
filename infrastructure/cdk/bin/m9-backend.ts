#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { M9BackendStack } from '../lib/m9-backend-stack';
import { M9FrontendStack } from '../lib/m9-frontend-stack';

const app = new cdk.App();

// Allow `--context desiredCount=0` on the first deploy (ECR is empty at that point).
const desiredCountCtx = app.node.tryGetContext('desiredCount');

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region:  process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
};

new M9BackendStack(app, 'M9Backend', {
  env,
  desiredCount: desiredCountCtx !== undefined ? Number(desiredCountCtx) : undefined,
  description:
    'Módulo 9 (Monitoreo) backend — cost-optimised university stack. ' +
    'WARNING: cdk destroy removes RDS + all data.',
});

new M9FrontendStack(app, 'M9Frontend', {
  env,
  // CloudFront is a global service; the bucket is created in the configured region.
  description: 'Módulo 9 (Monitoreo) frontend — S3 + CloudFront SPA hosting.',
});
