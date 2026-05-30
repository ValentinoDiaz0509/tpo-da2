#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { M9BackendStack } from '../lib/m9-backend-stack';

const app = new cdk.App();

new M9BackendStack(app, 'M9Backend', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region:  process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
  },
  description:
    'Módulo 9 (Monitoreo) backend — cost-optimised university stack. ' +
    'WARNING: cdk destroy removes RDS + all data.',
});
