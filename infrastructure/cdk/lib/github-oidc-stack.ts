import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as iam from 'aws-cdk-lib/aws-iam';

export interface GithubOidcStackProps extends cdk.StackProps {
  /** GitHub repository allowed to assume the deploy role, as "owner/repo". */
  githubRepo?: string;
  /**
   * Whether to create the GitHub OIDC provider. An account can only have ONE
   * provider per URL, and it is account-wide (not owned by this stack). Leave
   * this false (the default) to import the existing provider; set it true only
   * on an account that has never had a GitHub Actions OIDC provider.
   */
  createOidcProvider?: boolean;
}

/**
 * One-time stack: GitHub Actions OIDC federation.
 *
 * Deploy this ONCE manually (`npx cdk deploy GithubOidc`), copy the
 * `DeployRoleArn` output into the repo secret `AWS_DEPLOY_ROLE_ARN`, and the
 * pipeline needs no long-lived AWS keys.
 */
export class GithubOidcStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: GithubOidcStackProps = {}) {
    super(scope, id, props);

    const { githubRepo = 'ValentinoDiaz0509/tpo-da2', createOidcProvider = false } = props;

    // The provider is a single account-wide resource. Import the existing one
    // unless we're explicitly told to create it (fresh account).
    const provider: iam.IOpenIdConnectProvider = createOidcProvider
      ? new iam.OpenIdConnectProvider(this, 'GithubProvider', {
          url: 'https://token.actions.githubusercontent.com',
          clientIds: ['sts.amazonaws.com'],
        })
      : iam.OpenIdConnectProvider.fromOpenIdConnectProviderArn(
          this,
          'GithubProvider',
          `arn:aws:iam::${cdk.Stack.of(this).account}:oidc-provider/token.actions.githubusercontent.com`,
        );

    const deployRole = new iam.Role(this, 'DeployRole', {
      roleName: 'm9-github-deploy-role',
      description: `GitHub Actions deploy role for ${githubRepo} (main branch only).`,
      maxSessionDuration: cdk.Duration.hours(1),
      assumedBy: new iam.WebIdentityPrincipal(provider.openIdConnectProviderArn, {
        StringEquals: {
          'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
          'token.actions.githubusercontent.com:sub': `repo:${githubRepo}:ref:refs/heads/main`,
        },
      }),
    });

    // `cdk deploy` works by assuming the roles created by `cdk bootstrap`
    // (deploy, file-publishing, image-publishing, lookup).
    deployRole.addToPolicy(new iam.PolicyStatement({
      sid: 'AssumeCdkBootstrapRoles',
      actions: ['sts:AssumeRole'],
      resources: [`arn:aws:iam::${this.account}:role/cdk-*`],
    }));

    // Push the backend image (docker login + push in the workflow).
    deployRole.addToPolicy(new iam.PolicyStatement({
      sid: 'EcrLogin',
      actions: ['ecr:GetAuthorizationToken'],
      resources: ['*'],
    }));
    deployRole.addToPolicy(new iam.PolicyStatement({
      sid: 'EcrPush',
      actions: [
        'ecr:BatchCheckLayerAvailability',
        'ecr:BatchGetImage',
        'ecr:GetDownloadUrlForLayer',
        'ecr:InitiateLayerUpload',
        'ecr:UploadLayerPart',
        'ecr:CompleteLayerUpload',
        'ecr:PutImage',
      ],
      resources: [
        `arn:aws:ecr:${this.region}:${this.account}:repository/health-grid/m9-monitoring`,
      ],
    }));

    // Read stack outputs in the workflows: backend (ECR URI, cluster/service,
    // ALB URL) and frontend (bucket, distribution id).
    deployRole.addToPolicy(new iam.PolicyStatement({
      sid: 'ReadStackOutputs',
      actions: ['cloudformation:DescribeStacks'],
      resources: [
        `arn:aws:cloudformation:${this.region}:${this.account}:stack/M9Backend/*`,
        `arn:aws:cloudformation:${this.region}:${this.account}:stack/M9Frontend/*`,
      ],
    }));

    // Publish the built SPA to the frontend bucket (`aws s3 sync --delete`).
    // The bucket name is CDK-generated from the stack name, so scope by prefix.
    deployRole.addToPolicy(new iam.PolicyStatement({
      sid: 'SyncFrontendBucket',
      actions: ['s3:ListBucket', 's3:GetObject', 's3:PutObject', 's3:DeleteObject'],
      resources: [
        `arn:aws:s3:::m9frontend-*`,
        `arn:aws:s3:::m9frontend-*/*`,
      ],
    }));

    // Invalidate the CloudFront cache after each frontend deploy.
    deployRole.addToPolicy(new iam.PolicyStatement({
      sid: 'InvalidateFrontendCdn',
      actions: ['cloudfront:CreateInvalidation'],
      resources: [`arn:aws:cloudfront::${this.account}:distribution/*`],
    }));

    // Roll the ECS service onto the freshly pushed image.
    deployRole.addToPolicy(new iam.PolicyStatement({
      sid: 'RollEcsService',
      actions: ['ecs:UpdateService', 'ecs:DescribeServices'],
      resources: [
        `arn:aws:ecs:${this.region}:${this.account}:service/health-grid/m9-monitoring`,
      ],
    }));

    new cdk.CfnOutput(this, 'DeployRoleArn', {
      value: deployRole.roleArn,
      description: 'Set this as the GitHub repo secret AWS_DEPLOY_ROLE_ARN',
    });
  }
}
