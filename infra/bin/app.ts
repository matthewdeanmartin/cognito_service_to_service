#!/usr/bin/env node
import * as cdk from "aws-cdk-lib";
import { CognitoM2mStack } from "../lib/cognito-m2m-stack";

const app = new cdk.App();

new CognitoM2mStack(app, "CognitoM2mLocalDemoStack", {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? "us-east-1"
  }
});
