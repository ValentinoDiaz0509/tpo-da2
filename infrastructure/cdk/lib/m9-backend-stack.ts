import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as logs from 'aws-cdk-lib/aws-logs';

export interface M9BackendStackProps extends cdk.StackProps {
  /** Number of desired Fargate tasks. Pass 0 on first deploy (ECR is empty). */
  desiredCount?: number;
  taskCpu?: number;
  taskMemory?: number;
  dbInstanceType?: ec2.InstanceType;
  module6WebhookUrl?: string;
  jwtIssuer?: string;
}

export class M9BackendStack extends cdk.Stack {
  /** Public ALB DNS — use this to reach the API and Swagger. */
  public readonly loadBalancerUrl: cdk.CfnOutput;

  constructor(scope: Construct, id: string, props: M9BackendStackProps = {}) {
    super(scope, id, props);

    const {
      desiredCount    = 1,
      taskCpu         = 256,
      taskMemory      = 1024,
      dbInstanceType  = ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MICRO),
      module6WebhookUrl = 'https://internacion.healthgrid.com/webhooks/m9-alerts',
      jwtIssuer       = 'Module10-Core',
    } = props;

    // -------------------------------------------------------------------------
    // Networking — public subnets only (no NAT gateway, saves ~$33/mo)
    // NOTE: ECS tasks get a public IP. Fine for coursework, not production.
    // -------------------------------------------------------------------------
    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 2,
      ipAddresses: ec2.IpAddresses.cidr('10.0.0.0/16'),
      // No NAT gateways — cost saving.
      natGateways: 0,
      subnetConfiguration: [
        {
          name: 'public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
      ],
    });

    // -------------------------------------------------------------------------
    // Security groups
    // -------------------------------------------------------------------------
    const albSg = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc,
      description: 'ALB ingress (HTTP). Add HTTPS once an ACM cert is attached.',
    });
    albSg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80));

    const ecsSg = new ec2.SecurityGroup(this, 'EcsSg', {
      vpc,
      description: 'ECS tasks - only the ALB may reach the container.',
    });
    ecsSg.addIngressRule(albSg, ec2.Port.tcp(8080));

    const rdsSg = new ec2.SecurityGroup(this, 'RdsSg', {
      vpc,
      description: 'RDS - only ECS tasks may reach Postgres.',
    });
    rdsSg.addIngressRule(ecsSg, ec2.Port.tcp(5432));

    // -------------------------------------------------------------------------
    // ECR repository
    // -------------------------------------------------------------------------
    const repository = new ecr.Repository(this, 'EcrRepo', {
      repositoryName: 'health-grid/m9-monitoring',
      emptyOnDelete: true,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // -------------------------------------------------------------------------
    // Secrets
    // -------------------------------------------------------------------------
    const dbSecret = new secretsmanager.Secret(this, 'DbSecret', {
      secretName: 'm9/db-credentials',
      description: 'RDS master credentials (auto-generated password).',
      generateSecretString: {
        secretStringTemplate: JSON.stringify({ username: 'm9admin' }),
        generateStringKey: 'password',
        passwordLength: 24,
        excludePunctuation: true,
      },
    });

    const jwtSecret = new secretsmanager.Secret(this, 'JwtSecret', {
      secretName: 'm9/jwt-secret',
      description: 'HS512 signing secret for JWT validation.',
      generateSecretString: {
        passwordLength: 64,
        excludePunctuation: true,
      },
    });

    // -------------------------------------------------------------------------
    // RDS PostgreSQL (patients, rules, alerts)
    // -------------------------------------------------------------------------
    const database = new rds.DatabaseInstance(this, 'Database', {
      engine: rds.DatabaseInstanceEngine.postgres({
        version: rds.PostgresEngineVersion.VER_16_4,
      }),
      instanceType: dbInstanceType,
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      securityGroups: [rdsSg],
      databaseName: 'm9monitoring',
      credentials: rds.Credentials.fromSecret(dbSecret),
      multiAz: false,
      allocatedStorage: 20,
      storageEncrypted: true,
      publiclyAccessible: false,   // SG-locked to ECS
      backupRetention: cdk.Duration.days(0),
      deletionProtection: false,   // so cdk destroy works
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // -------------------------------------------------------------------------
    // SQS queues (names match spring.cloud.stream bindings in application.yml)
    // -------------------------------------------------------------------------
    const makeQueue = (id: string, name: string): sqs.Queue => {
      const dlq = new sqs.Queue(this, `${id}Dlq`, {
        queueName: `${name}-dlq`,
        retentionPeriod: cdk.Duration.days(4),
      });
      return new sqs.Queue(this, id, {
        queueName: name,
        visibilityTimeout: cdk.Duration.seconds(60),
        retentionPeriod: cdk.Duration.days(4),
        deadLetterQueue: { queue: dlq, maxReceiveCount: 3 },
      });
    };

    const patientEventsQueue    = makeQueue('PatientEventsQueue',    'patient-events-queue');
    const telemetryReadingsQueue = makeQueue('TelemetryReadingsQueue', 'telemetry-readings-queue');
    const admissionEventsQueue  = makeQueue('AdmissionEventsQueue',  'admission-events-queue');

    // -------------------------------------------------------------------------
    // CloudWatch log group
    // -------------------------------------------------------------------------
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: '/ecs/m9-monitoring',
      retention: logs.RetentionDays.THREE_DAYS,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // -------------------------------------------------------------------------
    // IAM — task execution role (pulls image, reads secrets)
    // -------------------------------------------------------------------------
    const executionRole = new iam.Role(this, 'TaskExecutionRole', {
      roleName: 'm9-ecs-execution-role',
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName(
          'service-role/AmazonECSTaskExecutionRolePolicy',
        ),
      ],
    });
    dbSecret.grantRead(executionRole);
    jwtSecret.grantRead(executionRole);

    // IAM — task role (app runtime: SQS)
    const taskRole = new iam.Role(this, 'TaskRole', {
      roleName: 'm9-monitoring-task-role',
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    });
    for (const q of [patientEventsQueue, telemetryReadingsQueue, admissionEventsQueue]) {
      q.grantConsumeMessages(taskRole);
      q.grantSendMessages(taskRole);
    }

    // -------------------------------------------------------------------------
    // ECS cluster + Fargate task definition
    // -------------------------------------------------------------------------
    const cluster = new ecs.Cluster(this, 'Cluster', {
      clusterName: 'health-grid',
      vpc,
      // Container Insights off — avoids CloudWatch custom-metric charges.
      containerInsightsV2: ecs.ContainerInsights.DISABLED,
      // Needed to run the service on FARGATE_SPOT (~70% cheaper).
      enableFargateCapacityProviders: true,
    });

    const taskDef = new ecs.FargateTaskDefinition(this, 'TaskDef', {
      family: 'm9-monitoring',
      cpu: taskCpu,
      memoryLimitMiB: taskMemory,
      executionRole,
      taskRole,
    });

    const container = taskDef.addContainer('m9-monitoring', {
      // The pipeline pushes the real image; :latest is rolled in on deploy.
      image: ecs.ContainerImage.fromEcrRepository(repository, 'latest'),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: 'm9',
        logGroup,
      }),
      environment: {
        SPRING_PROFILES_ACTIVE: 'prod',
        SERVER_PORT: '8080',
        AWS_REGION: this.region,
        AWS_SQS_REGION: this.region,
        AWS_SQS_ENABLED: 'true',
        SIMULATOR_ENABLED: 'false',
        MODULE6_WEBHOOK_URL: module6WebhookUrl,
        MODULE6_WEBHOOK_ALERTA_EMERGENCIA_URL: module6WebhookUrl,
        JWT_ISSUER: jwtIssuer,
        JWT_EXPIRATION: '86400000',
        APP_CORS_ALLOWED_ORIGIN_PATTERNS: 'http://localhost:3000,http://localhost:8080,http://localhost:5173,http://127.0.0.1:5173,https://*.cloudfront.net',
        SPRING_DATASOURCE_USERNAME: 'm9admin',
        SPRING_DATASOURCE_URL: `jdbc:postgresql://${database.dbInstanceEndpointAddress}:${database.dbInstanceEndpointPort}/m9monitoring`,
        AWS_SQS_ADMISSION_QUEUE: admissionEventsQueue.queueName,
        AWS_SQS_ADMISSION_QUEUE_URL: admissionEventsQueue.queueUrl,
      },
      secrets: {
        SPRING_DATASOURCE_PASSWORD: ecs.Secret.fromSecretsManager(dbSecret, 'password'),
        JWT_SECRET: ecs.Secret.fromSecretsManager(jwtSecret),
      },
      portMappings: [{ containerPort: 8080 }],
      healthCheck: {
        command: ['CMD-SHELL', 'curl -fsS http://localhost:8080/api/v1/actuator/health || exit 1'],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        // Generous start period: 0.25 vCPU JVM start is slow.
        startPeriod: cdk.Duration.seconds(120),
      },
    });

    // -------------------------------------------------------------------------
    // ALB + target group (stickiness for SockJS/WebSocket fallback)
    // -------------------------------------------------------------------------
    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      loadBalancerName: 'm9-monitoring-alb',
      vpc,
      internetFacing: true,
      securityGroup: albSg,
    });

    const targetGroup = new elbv2.ApplicationTargetGroup(this, 'TargetGroup', {
      targetGroupName: 'm9-monitoring-tg',
      vpc,
      protocol: elbv2.ApplicationProtocol.HTTP,
      port: 8080,
      targetType: elbv2.TargetType.IP,
      healthCheck: {
        path: '/api/v1/actuator/health',
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        healthyThresholdCount: 2,
      },
      stickinessCookieDuration: cdk.Duration.hours(1),
    });

    alb.addListener('HttpListener', {
      port: 80,
      defaultTargetGroups: [targetGroup],
    });

    // -------------------------------------------------------------------------
    // ECS service
    // NOTE: public subnets + ENABLED public IP replaces the NAT gateway.
    // -------------------------------------------------------------------------
    new ecs.FargateService(this, 'Service', {
      serviceName: 'm9-monitoring',
      cluster,
      taskDefinition: taskDef,
      desiredCount,
      // Fargate Spot: interruptible (2-min notice) but ~70% cheaper — acceptable
      // for a university demo. Remove this block to fall back to on-demand.
      capacityProviderStrategies: [
        { capacityProvider: 'FARGATE_SPOT', weight: 1 },
      ],
      assignPublicIp: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      securityGroups: [ecsSg],
      healthCheckGracePeriod: cdk.Duration.seconds(180),
      // Roll safely: keep 100% running until the new task is healthy, then stop the old one.
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
      // Circuit breaker: fail fast if the new task can't start (vs. waiting 3 hours).
      circuitBreaker: { enable: true, rollback: true },
    }).attachToApplicationTargetGroup(targetGroup);

    // -------------------------------------------------------------------------
    // Outputs
    // -------------------------------------------------------------------------
    this.loadBalancerUrl = new cdk.CfnOutput(this, 'LoadBalancerUrl', {
      value: `http://${alb.loadBalancerDnsName}`,
      description: 'Public base URL (append /api/v1)',
    });

    new cdk.CfnOutput(this, 'EcrRepositoryUri', {
      value: repository.repositoryUri,
      description: 'Push images here (used by the GitHub Actions pipeline)',
    });

    new cdk.CfnOutput(this, 'ClusterName', { value: cluster.clusterName });
    new cdk.CfnOutput(this, 'ServiceName', { value: 'm9-monitoring' });
    new cdk.CfnOutput(this, 'DbEndpoint',  { value: database.dbInstanceEndpointAddress });
  }
}
