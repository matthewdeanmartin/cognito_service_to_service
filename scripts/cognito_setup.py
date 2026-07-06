#!/usr/bin/env python3
"""Provision (or tear down) the Cognito M2M resources with boto3.

A non-CDK alternative to ``infra/`` that is idempotent and works against either
real AWS or a local moto server. It creates the same shape the CDK stack does:

  * a user pool
  * a resource server ``secondary`` with scope ``read``  (-> ``secondary/read``)
  * an app client for ``primary`` with the client-credentials grant + a secret
  * a hosted domain (best effort; moto accepts it but does not serve tokens)

and then writes ``.env.local`` so the Spring services can pick the values up.

Commands
--------
  setup     create anything missing (skips what already exists), write .env.local
  status    show what currently exists
  teardown  delete the user pool (and everything under it)

Endpoint
--------
  --endpoint-url http://localhost:5000   talk to a local moto server
  (omit it to talk to real AWS using your normal credentials)

Examples
--------
  uv run python scripts/cognito_setup.py setup --endpoint-url http://localhost:5000
  uv run python scripts/cognito_setup.py status --endpoint-url http://localhost:5000
  uv run python scripts/cognito_setup.py teardown --endpoint-url http://localhost:5000
"""
from __future__ import annotations

import argparse
import os
import sys

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

# --- desired configuration (mirrors infra/lib/cognito-m2m-stack.ts) -----------
POOL_NAME = "local-m2m-demo-pool"
RESOURCE_SERVER_ID = "secondary"
RESOURCE_SERVER_NAME = "secondary"
SCOPE_NAME = "read"
SCOPE_DESCRIPTION = "Read labels from secondary service"
FULL_SCOPE = f"{RESOURCE_SERVER_ID}/{SCOPE_NAME}"
CLIENT_NAME = "primary-service-client"
DOMAIN_PREFIX = "local-m2m-demo"  # must be globally unique on real AWS
ENV_FILE = ".env.local"


def make_client(region: str, endpoint_url: str | None):
    """A cognito-idp client. For moto we inject dummy credentials so boto3 is
    happy even when the environment has none configured."""
    kwargs = dict(region_name=region, config=Config(retries={"max_attempts": 2}))
    if endpoint_url:
        kwargs.update(
            endpoint_url=endpoint_url,
            aws_access_key_id="testing",
            aws_secret_access_key="testing",
            aws_session_token="testing",
        )
    return boto3.client("cognito-idp", **kwargs)


# --- lookups (idempotency helpers) -------------------------------------------
def find_pool(client) -> str | None:
    paginator = client.get_paginator("list_user_pools")
    for page in paginator.paginate(MaxResults=60):
        for pool in page["UserPools"]:
            if pool["Name"] == POOL_NAME:
                return pool["Id"]
    return None


def find_client(client, pool_id: str) -> str | None:
    paginator = client.get_paginator("list_user_pool_clients")
    for page in paginator.paginate(UserPoolId=pool_id, MaxResults=60):
        for c in page["UserPoolClients"]:
            if c["ClientName"] == CLIENT_NAME:
                return c["ClientId"]
    return None


def has_resource_server(client, pool_id: str) -> bool:
    try:
        client.describe_resource_server(
            UserPoolId=pool_id, Identifier=RESOURCE_SERVER_ID
        )
        return True
    except ClientError as e:
        if e.response["Error"]["Code"] == "ResourceNotFoundException":
            return False
        raise


# --- build-up (each step skips if already present) ---------------------------
def ensure_pool(client) -> str:
    pool_id = find_pool(client)
    if pool_id:
        print(f"  skip  user pool exists: {pool_id}")
        return pool_id
    pool_id = client.create_user_pool(PoolName=POOL_NAME)["UserPool"]["Id"]
    print(f"  create  user pool: {pool_id}")
    return pool_id


def ensure_resource_server(client, pool_id: str) -> None:
    if has_resource_server(client, pool_id):
        print(f"  skip  resource server exists: {RESOURCE_SERVER_ID}")
        return
    client.create_resource_server(
        UserPoolId=pool_id,
        Identifier=RESOURCE_SERVER_ID,
        Name=RESOURCE_SERVER_NAME,
        Scopes=[{"ScopeName": SCOPE_NAME, "ScopeDescription": SCOPE_DESCRIPTION}],
    )
    print(f"  create  resource server: {RESOURCE_SERVER_ID} (scope {FULL_SCOPE})")


def domain_exists(client) -> bool:
    try:
        desc = client.describe_user_pool_domain(Domain=DOMAIN_PREFIX)
        # A never-created domain returns an empty DomainDescription rather than
        # a 404, so check for a concrete field.
        return bool(desc.get("DomainDescription", {}).get("UserPoolId"))
    except ClientError as e:
        if e.response["Error"]["Code"] == "ResourceNotFoundException":
            return False
        raise


def ensure_domain(client, pool_id: str) -> None:
    if domain_exists(client):
        print(f"  skip  domain exists: {DOMAIN_PREFIX}")
        return
    try:
        client.create_user_pool_domain(Domain=DOMAIN_PREFIX, UserPoolId=pool_id)
        print(f"  create  domain: {DOMAIN_PREFIX}")
    except ClientError as e:
        code = e.response["Error"]["Code"]
        # Taken by someone else on real AWS — fine for an idempotent setup.
        if code in ("InvalidParameterException", "ResourceConflictException"):
            print(f"  skip  domain already taken ({code})")
        else:
            raise


def ensure_client(client, pool_id: str) -> tuple[str, str]:
    client_id = find_client(client, pool_id)
    if not client_id:
        created = client.create_user_pool_client(
            UserPoolId=pool_id,
            ClientName=CLIENT_NAME,
            GenerateSecret=True,
            AllowedOAuthFlows=["client_credentials"],
            AllowedOAuthScopes=[FULL_SCOPE],
            AllowedOAuthFlowsUserPoolClient=True,
        )["UserPoolClient"]
        client_id = created["ClientId"]
        print(f"  create  app client: {client_id}")
    else:
        print(f"  skip  app client exists: {client_id}")
    # Fetch the secret (create response has it, but describe is uniform for both paths).
    desc = client.describe_user_pool_client(UserPoolId=pool_id, ClientId=client_id)
    secret = desc["UserPoolClient"].get("ClientSecret", "")
    return client_id, secret


# --- outputs -----------------------------------------------------------------
def env_values(region, pool_id, client_id, secret, endpoint_url) -> dict[str, str]:
    issuer = f"https://cognito-idp.{region}.amazonaws.com/{pool_id}"
    if endpoint_url:
        # moto serves JWKS off its own host; there is no real hosted-UI token
        # endpoint, so token-url points at the local Flask shim used in tests.
        # See docs/running/testing-with-moto.md.
        domain_url = endpoint_url.rstrip("/")
        token_url = "http://localhost:9999/oauth2/token"
        issuer = f"{endpoint_url.rstrip('/')}/{pool_id}"
        jwks = f"{issuer}/.well-known/jwks.json"
    else:
        domain_url = f"https://{DOMAIN_PREFIX}.auth.{region}.amazoncognito.com"
        token_url = f"{domain_url}/oauth2/token"
        jwks = f"{issuer}/.well-known/jwks.json"
    return {
        "COGNITO_REGION": region,
        "COGNITO_USER_POOL_ID": pool_id,
        "COGNITO_DOMAIN_URL": domain_url,
        "COGNITO_TOKEN_URL": token_url,
        "COGNITO_ISSUER_URI": issuer,
        "COGNITO_JWKS_URI": jwks,
        "COGNITO_PRIMARY_CLIENT_ID": client_id,
        "COGNITO_PRIMARY_CLIENT_SECRET": secret,
        "COGNITO_SCOPE": FULL_SCOPE,
        "SECONDARY_BASE_URL": "http://localhost:8082",
    }


def write_env(values: dict[str, str]) -> None:
    with open(ENV_FILE, "w", encoding="utf-8", newline="\n") as f:
        for k, v in values.items():
            f.write(f"{k}={v}\n")
    print(f"  wrote  {ENV_FILE}")


# --- commands ----------------------------------------------------------------
def cmd_setup(client, region, endpoint_url) -> int:
    print("setup:")
    pool_id = ensure_pool(client)
    ensure_resource_server(client, pool_id)
    ensure_domain(client, pool_id)
    client_id, secret = ensure_client(client, pool_id)
    write_env(env_values(region, pool_id, client_id, secret, endpoint_url))
    if endpoint_url:
        print(
            "\nNote: moto does not serve the Cognito /oauth2/token endpoint.\n"
            "For a full local run, start the token/JWKS shim as described in\n"
            "docs/running/testing-with-moto.md."
        )
    print("setup: done")
    return 0


def cmd_status(client, region, endpoint_url) -> int:
    print("status:")
    pool_id = find_pool(client)
    if not pool_id:
        print("  user pool: (none)")
        return 0
    print(f"  user pool: {pool_id}")
    print(f"  resource server '{RESOURCE_SERVER_ID}': "
          f"{'present' if has_resource_server(client, pool_id) else 'MISSING'}")
    client_id = find_client(client, pool_id)
    print(f"  app client '{CLIENT_NAME}': {client_id or 'MISSING'}")
    return 0


def cmd_teardown(client, region, endpoint_url) -> int:
    print("teardown:")
    pool_id = find_pool(client)
    if not pool_id:
        print("  skip  no user pool to delete")
        return 0
    # A pool with a domain must have the domain deleted first on real AWS.
    try:
        client.delete_user_pool_domain(Domain=DOMAIN_PREFIX, UserPoolId=pool_id)
        print(f"  delete  domain: {DOMAIN_PREFIX}")
    except ClientError as e:
        print(f"  skip  domain delete ({e.response['Error']['Code']})")
    client.delete_user_pool(UserPoolId=pool_id)
    print(f"  delete  user pool: {pool_id}")
    print("teardown: done")
    return 0


COMMANDS = {"setup": cmd_setup, "status": cmd_status, "teardown": cmd_teardown}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=COMMANDS)
    parser.add_argument("--region", default=os.environ.get("AWS_REGION", "us-east-1"))
    parser.add_argument("--endpoint-url", default=os.environ.get("COGNITO_ENDPOINT_URL"),
                        help="talk to a local moto server, e.g. http://localhost:5000")
    args = parser.parse_args(argv)

    client = make_client(args.region, args.endpoint_url)
    try:
        return COMMANDS[args.command](client, args.region, args.endpoint_url)
    except ClientError as e:
        print(f"AWS error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
