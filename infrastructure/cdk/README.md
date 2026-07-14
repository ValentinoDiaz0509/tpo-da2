# M9 Infrastructure (AWS CDK, TypeScript)

CDK app for the Módulo 9 backend. Source of truth for the intended architecture:
`docs/architecture/diagrams/m9_architecture.py`.

## Stacks

| Stack | Purpose |
|-------|---------|
| `M9Backend` | Everything the backend needs: VPC (public subnets, **no NAT**), ALB, ECS Fargate **Spot** service, ECR repo, RDS PostgreSQL 16 (`db.t4g.micro`, single-AZ), 3 SQS queues + DLQs, Secrets Manager (DB creds, JWT), CloudWatch logs. |
| `M9Frontend` | S3 + CloudFront SPA hosting (managed separately — out of scope for the backend pipeline). |
| `GithubOidc` | One-time: GitHub Actions OIDC provider + `m9-github-deploy-role`. No long-lived AWS keys in GitHub. |

All backend resources are tagged `project=m9`, `env=university` and use
`RemovalPolicy.DESTROY` so `cdk destroy` tears everything down between grading
cycles. **`cdk destroy M9Backend` deletes the RDS data.**

## First deploy (manual, once)

```bash
npm ci
npx cdk bootstrap                                  # once per account/region

# 1. OIDC federation for the pipeline
npx cdk deploy GithubOidc
#    → copy the DeployRoleArn output into the GitHub repo secret AWS_DEPLOY_ROLE_ARN

# 2. Backend infra — desiredCount=0 because ECR is still empty
npx cdk deploy M9Backend --context desiredCount=0

# 3. Push the first image (use the EcrRepositoryUri output)
aws ecr get-login-password | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
docker build -t <EcrRepositoryUri>:latest ../../backend
docker push <EcrRepositoryUri>:latest

# 4. Scale the service up
npx cdk deploy M9Backend
```

After that, every push to `main` touching `backend/**` or `infrastructure/cdk/**`
runs `.github/workflows/deploy-backend.yml`: backend tests → `cdk synth` →
`cdk deploy M9Backend` → docker build/push → roll the ECS service.

## Useful commands

```bash
npm test           # jest assertions on the synthesized templates
npx cdk synth M9Backend
npx cdk diff M9Backend    # ALWAYS diff before a manual deploy
npx cdk destroy M9Backend # tear down (deletes RDS data)
```

## Monthly cost (us-east-1, rough)

| Resource | Cost | Free tier? |
|----------|------|-----------|
| Fargate Spot, 1 task 0.25 vCPU / 1 GB | ~$3/mo | No (Spot ≈ 70% off on-demand) |
| ALB | ~$16/mo + LCU | No — the single biggest line item |
| RDS `db.t4g.micro` 20 GB, single-AZ | ~$12/mo (or $0 first 12 months) | Yes, 750 h/mo on new accounts |
| SQS / Secrets Manager / CloudWatch / ECR | ~$1/mo combined | Mostly (1M SQS req, 0.5 GB ECR; secrets are ~$0.40 each after 30-day trial) |
| NAT Gateway | $0 — **deliberately none** | Tasks get public IPs instead |

Total ≈ **$20–32/mo** while running. `cdk destroy M9Backend` when not demoing.
