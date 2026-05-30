# Backend Deployment — Operator Guide

This guide explains how to stand up and operate the **Módulo 9 (Monitoreo)**
backend on AWS.

Responsibilities are **split** between two pieces:

| Concern | Owned by | File |
|---------|----------|------|
| **Infrastructure lifecycle** (create/destroy): VPC, ALB, ECS cluster/service/task def, ECR, SQS (+ DLQs), RDS PostgreSQL, DynamoDB, IAM, logs | **CloudFormation stack** | `infra/cloudformation/m9-backend.yaml` |
| **Per-commit app deploys**: build image, push to ECR, roll the ECS service | **GitHub Actions** | `.github/workflows/deploy-backend.yml` |

The whole Module 9 backend is **one CloudFormation stack** — create it once, and
tear the entire thing down (VPC, database, queues, everything) with a single
`delete-stack`.

> ⚠️ **Deleting the stack destroys the RDS database and all DynamoDB telemetry.**
> `DeletionProtection` is intentionally off so teardown is one command. Don't run
> it against an environment whose data you care about.

---

## 1. What do I have to do manually?

Almost everything is in the CloudFormation stack. The only things you set up by
hand are the things the stack/pipeline can't bootstrap for themselves:

| # | Manual step | Why it can't be in the stack |
|---|-------------|------------------------------|
| 1 | **GitHub OIDC provider + deploy IAM role** in AWS, and the repo secret `AWS_DEPLOY_ROLE_ARN` | This is the identity the pipeline uses *before* it can touch AWS — see [§2](#2-how-to-authenticate-in-aws). |
| 2 | **Deploy the CloudFormation stack** | Creating the stack is the manual "go" action — see [§3](#3-deploy-the-infrastructure-stack). |
| 3 | **Push the first image** (run the pipeline once) | ECR starts empty; ECS tasks can't run until an image exists — see [§3](#3-deploy-the-infrastructure-stack) bootstrap note. |

Everything else (queues, database, table, ALB, networking, roles) is created and
named by the stack. You do **not** run any `aws sqs create-queue` /
`aws ecs create-service` / `aws rds create-db-instance` commands by hand.

---

## 2. How to authenticate in AWS?

The pipeline uses **GitHub OIDC** to assume an IAM role — there are **no
long-lived AWS access keys** stored in GitHub.

> Reference: [Configuring OpenID Connect in Amazon Web Services](https://docs.github.com/en/actions/how-tos/security-for-github-actions/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services)

### Step 2.1 — Create the GitHub OIDC identity provider (once per account)

```bash
aws iam create-open-id-connect-provider \
    --url https://token.actions.githubusercontent.com \
    --client-id-list sts.amazonaws.com
```

### Step 2.2 — Create the deploy role, locked to this repo's `main`

Replace `<ACCOUNT_ID>` and `<OWNER>/<REPO>`.

`trust-policy.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:<OWNER>/<REPO>:ref:refs/heads/main"
        }
      }
    }
  ]
}
```

```bash
aws iam create-role \
    --role-name github-actions-deploy \
    --assume-role-policy-document file://trust-policy.json
```

### Step 2.3 — Attach permissions (just what the pipeline needs)

Because CloudFormation now owns the infrastructure, the *pipeline's* role only
needs to push images and roll the service — much narrower than before.

`deploy-permissions.json`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcrPush",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    },
    {
      "Sid": "EcsRollService",
      "Effect": "Allow",
      "Action": [
        "ecs:UpdateService",
        "ecs:DescribeServices"
      ],
      "Resource": "*"
    }
  ]
}
```

```bash
aws iam put-role-policy \
    --role-name github-actions-deploy \
    --policy-name m9-deploy-policy \
    --policy-document file://deploy-permissions.json
```

> If you also want to run `cloudformation deploy` from CI later, that role would
> need broader permissions. Today the stack is deployed manually from your own
> AWS credentials (admin/CloudFormation), so the pipeline role stays minimal.

### Step 2.4 — Store the role ARN as a repo secret

```bash
gh secret set AWS_DEPLOY_ROLE_ARN \
    --body "arn:aws:iam::<ACCOUNT_ID>:role/github-actions-deploy"
```

Or via the UI: **Settings → Secrets and variables → Actions → New repository
secret**, name `AWS_DEPLOY_ROLE_ARN`.

---

## 3. Deploy the infrastructure stack

Run these from your own (admin/CloudFormation-capable) AWS credentials — this is
the manual "go" action that creates the whole environment.

> ### 💸 Easiest path: the helper scripts
>
> Because this is a university stack you won't keep running, use the wrappers in
> `infra/scripts/` — they handle the bootstrap ordering for you:
>
> ```bash
> ./infra/scripts/up.sh        # create stack + build/push image + scale up
> ./infra/scripts/status.sh    # is it up? how many tasks? rough cost
> ./infra/scripts/down.sh      # delete everything -> idle cost ~$0
> ```
>
> `up.sh` is idempotent (re-run it to ship a new build); `down.sh` deletes the
> stack — **including the database**. This cost-optimized stack (no NAT gateway,
> 0.25 vCPU task, no backups/PITR) costs roughly **$40–50/mo** if left up, so run
> `down.sh` when you're done for the day. The manual commands below are what
> these scripts run under the hood.

### 3.1 — First-time create (bootstrap order)

ECR is empty at creation, so ECS can't run a healthy task yet. Create the stack
with **`DesiredCount=0`** so it stands up cleanly, then push an image, then scale up.

```bash
# 1. Create everything with zero running tasks
aws cloudformation deploy \
    --stack-name m9-backend \
    --template-file infra/cloudformation/m9-backend.yaml \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides DesiredCount=0

# 2. Push the first image (see §4 — run the GitHub Actions pipeline,
#    or build & push locally to the ECR repo from the stack outputs)

# 3. Scale the service up now that an image exists
aws cloudformation deploy \
    --stack-name m9-backend \
    --template-file infra/cloudformation/m9-backend.yaml \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides DesiredCount=2
```

`CAPABILITY_NAMED_IAM` is required because the stack creates named IAM roles.

### 3.2 — Useful parameters

Override with `--parameter-overrides Key=Value ...`:

| Parameter | Default | Notes |
|-----------|---------|-------|
| `DesiredCount` | `1` | Number of Fargate tasks. Use `0` for first create. |
| `TaskCpu` / `TaskMemory` | `512` / `1024` | Fargate sizing. |
| `DbInstanceClass` | `db.t3.micro` | RDS size. |
| `Module6WebhookUrl` | internación URL | Where alerts are POSTed. |
| `EnvironmentName` | `m9` | Prefix for resource names. |

### 3.3 — Inspect what was created

```bash
aws cloudformation describe-stacks --stack-name m9-backend \
    --query 'Stacks[0].Outputs'
```

Outputs include the **ALB URL**, **ECR repo URI**, DB endpoint, and table name.

### 3.4 — Tear it all down (one click)

```bash
aws cloudformation delete-stack --stack-name m9-backend
aws cloudformation wait stack-delete-complete --stack-name m9-backend
```

This removes the VPC, ALB, ECS, SQS, RDS, DynamoDB, ECR (images included via
`EmptyOnDelete`), IAM roles, and logs. **The database and telemetry are gone.**

---

## 4. How to run the pipeline (app deploys)?

Once the stack exists, shipping a new backend version is just a code push.

### Automatically (the normal path)

The pipeline runs on **every push to `main` that changes anything under
`backend/`** (or the workflow file):

```yaml
on:
  push:
    branches: [main]
    paths:
      - "backend/**"
      - ".github/workflows/deploy-backend.yml"
```

```bash
git switch main && git pull
# ... backend changes, commit ...
git push origin main
```

It builds from `backend/Dockerfile`, pushes `:<sha>` and `:latest` to ECR, then
`force-new-deployment` rolls the ECS service onto the new `:latest` and waits for
stability. (The CloudFormation task definition pins the container to `:latest`,
so no task-definition edits happen in CI — that avoids drift from the stack.)

### Manually (on demand)

```bash
gh workflow run deploy-backend.yml --ref main
```

…or **Actions → Deploy backend → Run workflow**.

### Watching a run

```bash
gh run watch
gh run list --workflow deploy-backend.yml
gh run view --log
```

---

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | OIDC provider missing (Step 2.1) or trust policy `sub`/`aud` mismatch (Step 2.2). |
| `AccessDenied` on `ecs:UpdateService` or ECR push | Pipeline role policy incomplete (Step 2.3). |
| Stack create rolls back on `EcsService` | Created with `DesiredCount>0` while ECR is empty — recreate with `DesiredCount=0` first (§3.1). |
| `delete-stack` stuck on ECR repo | Should not happen (`EmptyOnDelete: true`); if it does, an image was pushed to a tag the stack doesn't manage — empty the repo manually. |
| `delete-stack` stuck on RDS | Re-check `DeletionProtection` is `false` (it is in the template). |
| Service never stabilizes after deploy | Container failing health checks — inspect CloudWatch logs `/ecs/m9-monitoring`; common causes are bad DB connectivity or `SIMULATOR_ENABLED` not `false`. |

---

**Related docs:** [`docs/architecture/aws-deployment-guide.md`](../architecture/aws-deployment-guide.md) (architecture rationale & manual AWS CLI reference) ·
[`docs/guides/DEVELOPMENT_GUIDE.md`](./DEVELOPMENT_GUIDE.md) (local dev).
