#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { M9BackendStack } from '../lib/m9-backend-stack';
import { M9FrontendStack } from '../lib/m9-frontend-stack';
import { GithubOidcStack } from '../lib/github-oidc-stack';

const app = new cdk.App();

// Allow `--context desiredCount=0` on the first deploy (ECR is empty at that point).
const desiredCountCtx = app.node.tryGetContext('desiredCount');

// Target account: 521764600474 (nicochavez-uade), pinned in cdk.json so the
// stack isn't environment-agnostic. CDK_DEFAULT_ACCOUNT still wins if set
// (e.g. a different account in CI), otherwise this fallback applies.
const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT ?? app.node.tryGetContext('accountId'),
  region:  process.env.CDK_DEFAULT_REGION ?? 'us-east-1',
};

const backend = new M9BackendStack(app, 'M9Backend', {
  env,
  desiredCount: desiredCountCtx !== undefined ? Number(desiredCountCtx) : undefined,
  description:
    'Módulo 9 (Monitoreo) backend — cost-optimised university stack. ' +
    'WARNING: cdk destroy removes RDS + all data.',
});

// One-time stack: deploy manually, put DeployRoleArn in the repo secret
// AWS_DEPLOY_ROLE_ARN (see lib/github-oidc-stack.ts).
const oidc = new GithubOidcStack(app, 'GithubOidc', {
  env,
  // The account already has a GitHub OIDC provider, so we import it by default.
  // On a brand-new account that has never had one, deploy with
  // `--context createOidcProvider=true` to create it instead.
  createOidcProvider: app.node.tryGetContext('createOidcProvider') === 'true',
  description: 'GitHub Actions OIDC provider + deploy role (no long-lived keys).',
});

// Cost-tracking tags (backend stacks only — frontend is out of scope).
for (const stack of [backend, oidc]) {
  cdk.Tags.of(stack).add('project', 'm9');
  cdk.Tags.of(stack).add('env', 'university');
}

new M9FrontendStack(app, 'M9Frontend', {
  env,
  // CloudFront is a global service; the bucket is created in the configured region.
  description: 'Módulo 9 (Monitoreo) frontend — S3 + CloudFront SPA hosting.',
});
