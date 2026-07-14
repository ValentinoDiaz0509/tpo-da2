import * as cdk from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { M9BackendStack } from '../lib/m9-backend-stack';
import { GithubOidcStack } from '../lib/github-oidc-stack';

describe('M9BackendStack', () => {
  const app = new cdk.App();
  const stack = new M9BackendStack(app, 'TestStack');
  const template = Template.fromStack(stack);

  test('runs the service on Fargate Spot', () => {
    template.hasResourceProperties('AWS::ECS::Service', {
      CapacityProviderStrategy: [{ CapacityProvider: 'FARGATE_SPOT', Weight: 1 }],
    });
  });

  test('creates the three app queues plus their DLQs', () => {
    template.resourceCountIs('AWS::SQS::Queue', 6);
    for (const name of ['patient-events-queue', 'telemetry-readings-queue', 'admission-events-queue']) {
      template.hasResourceProperties('AWS::SQS::Queue', { QueueName: name });
    }
  });

  test('RDS is single-AZ, deletable, and not publicly accessible', () => {
    template.hasResourceProperties('AWS::RDS::DBInstance', {
      MultiAZ: false,
      DeletionProtection: false,
      PubliclyAccessible: false,
    });
  });

  test('no NAT gateways (cost constraint)', () => {
    template.resourceCountIs('AWS::EC2::NatGateway', 0);
  });

  test('no DynamoDB table (telemetry lives in Postgres)', () => {
    template.resourceCountIs('AWS::DynamoDB::Table', 0);
  });
});

describe('GithubOidcStack', () => {
  const app = new cdk.App();
  const stack = new GithubOidcStack(app, 'TestOidc');
  const template = Template.fromStack(stack);

  test('deploy role is restricted to the repo main branch', () => {
    template.hasResourceProperties('AWS::IAM::Role', {
      AssumeRolePolicyDocument: {
        Statement: [
          {
            Action: 'sts:AssumeRoleWithWebIdentity',
            Condition: {
              StringEquals: {
                'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
                'token.actions.githubusercontent.com:sub':
                  'repo:ValentinoDiaz0509/tpo-da2:ref:refs/heads/main',
              },
            },
          },
        ],
      },
    });
  });
});
