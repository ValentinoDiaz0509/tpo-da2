import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as cloudfront from 'aws-cdk-lib/aws-cloudfront';
import * as origins from 'aws-cdk-lib/aws-cloudfront-origins';

export interface M9FrontendStackProps extends cdk.StackProps {
  /**
   * DNS name of the backend ALB (no scheme), used as the origin for the `/api/*`
   * behavior. Supplied at deploy time from the M9Backend `LoadBalancerUrl` output
   * (see .github/workflows/deploy-frontend.yml). When absent (e.g. a bare
   * `cdk synth`) a placeholder is used so synth still succeeds — the /api origin
   * just won't resolve until a real value is passed on deploy.
   */
  albDomainName?: string;
}

export class M9FrontendStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: M9FrontendStackProps) {
    super(scope, id, props);

    // Private bucket — CloudFront is the only origin.
    const bucket = new s3.Bucket(this, 'FrontendBucket', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
    });

    // Backend ALB origin for the API proxy. The ALB listens on HTTP :80, so the
    // CloudFront->origin hop is HTTP; the viewer->CloudFront hop stays HTTPS,
    // which is what removes the mixed-content problem (browser only sees HTTPS).
    const albDomainName = props?.albDomainName ?? 'alb-domain-not-set.invalid';
    const apiOrigin = new origins.HttpOrigin(albDomainName, {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
      httpPort: 80,
    });

    const distribution = new cloudfront.Distribution(this, 'Distribution', {
      // Default behavior: the static SPA served from S3.
      defaultBehavior: {
        origin: origins.S3BucketOrigin.withOriginAccessControl(bucket),
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
      },
      // Everything under /api/* (REST + /api/v1/ws WebSocket) is proxied to the
      // backend ALB so the frontend can call it same-origin over HTTPS.
      additionalBehaviors: {
        '/api/*': {
          origin: apiOrigin,
          viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          // APIs use every verb, so allow all methods.
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          // Never cache API responses.
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          // Forward Authorization + the WebSocket upgrade headers + the ALB
          // stickiness cookie to the origin (everything except Host).
          originRequestPolicy:
            cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
        },
      },
      defaultRootObject: 'index.html',
      // React Router SPA: return index.html on S3 404/403 so client-side routing works.
      errorResponses: [
        {
          httpStatus: 403,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: cdk.Duration.seconds(0),
        },
        {
          httpStatus: 404,
          responseHttpStatus: 200,
          responsePagePath: '/index.html',
          ttl: cdk.Duration.seconds(0),
        },
      ],
    });

    new cdk.CfnOutput(this, 'BucketName', {
      value: bucket.bucketName,
      description: 'S3 bucket — set as FRONTEND_BUCKET_NAME GitHub variable.',
    });

    new cdk.CfnOutput(this, 'DistributionId', {
      value: distribution.distributionId,
      description: 'CloudFront distribution — set as FRONTEND_DISTRIBUTION_ID GitHub variable.',
    });

    new cdk.CfnOutput(this, 'CloudFrontUrl', {
      value: `https://${distribution.distributionDomainName}`,
      description: 'Public URL of the frontend SPA.',
    });
  }
}
