import * as cdk from "aws-cdk-lib";
import { Construct } from "constructs";
import * as cognito from "aws-cdk-lib/aws-cognito";

export class CognitoM2mStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const userPool = new cognito.UserPool(this, "UserPool", {
      userPoolName: "local-m2m-demo-pool",
      selfSignUpEnabled: false,
      removalPolicy: cdk.RemovalPolicy.DESTROY
    });

    const domainPrefix = `local-m2m-demo-${cdk.Names.uniqueId(this).toLowerCase()}`.slice(0, 63);

    const domain = userPool.addDomain("Domain", {
      cognitoDomain: {
        domainPrefix
      }
    });

    const resourceServer = userPool.addResourceServer("SecondaryResourceServer", {
      identifier: "secondary",
      userPoolResourceServerName: "secondary",
      scopes: [
        {
          scopeName: "read",
          scopeDescription: "Read labels from secondary service"
        }
      ]
    });

    const primaryClient = userPool.addClient("PrimaryServiceClient", {
      userPoolClientName: "primary-service-client",
      generateSecret: true,
      oAuth: {
        flows: {
          clientCredentials: true
        },
        scopes: [
          cognito.OAuthScope.resourceServer(resourceServer, {
            scopeName: "read",
            scopeDescription: "Read labels from secondary service"
          })
        ]
      }
    });

    const region = cdk.Stack.of(this).region;
    const issuerUri = `https://cognito-idp.${region}.amazonaws.com/${userPool.userPoolId}`;
    const domainUrl = domain.baseUrl();
    const tokenUrl = `${domainUrl}/oauth2/token`;

    new cdk.CfnOutput(this, "CognitoRegion", { value: region });
    new cdk.CfnOutput(this, "CognitoUserPoolId", { value: userPool.userPoolId });
    new cdk.CfnOutput(this, "CognitoDomainUrl", { value: domainUrl });
    new cdk.CfnOutput(this, "CognitoTokenUrl", { value: tokenUrl });
    new cdk.CfnOutput(this, "CognitoIssuerUri", { value: issuerUri });
    new cdk.CfnOutput(this, "CognitoJwksUri", {
      value: `${issuerUri}/.well-known/jwks.json`
    });
    new cdk.CfnOutput(this, "CognitoPrimaryClientId", {
      value: primaryClient.userPoolClientId
    });
    new cdk.CfnOutput(this, "CognitoScope", { value: "secondary/read" });

    /**
     * CDK does not expose the generated client secret as a plain output
     * in the high-level construct. For the PoC, fetch it with:
     *
     * aws cognito-idp describe-user-pool-client \
     *   --user-pool-id <pool-id> \
     *   --client-id <client-id> \
     *   --query 'UserPoolClient.ClientSecret' \
     *   --output text
     *
     * scripts/export-cdk-outputs.sh does exactly this.
     */
  }
}
