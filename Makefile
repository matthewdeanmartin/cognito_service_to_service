.PHONY: help install deploy destroy export-env run-primary run-secondary run-ui test-demo clean docs docs-serve run-moto run-robotocore stop-robotocore cognito-setup cognito-status cognito-teardown

# Endpoint for the boto3 Cognito script: local moto by default. Set to empty
# (make cognito-setup COGNITO_ENDPOINT_URL=) to target real AWS.
COGNITO_ENDPOINT_URL ?= http://localhost:5111

help:
	@echo "Targets:"
	@echo "  make install        Install CDK/UI deps"
	@echo "  make deploy         Deploy Cognito stack"
	@echo "  make export-env     Write .env.local from CDK outputs"
	@echo "  make run-primary    Run primary Spring Boot service"
	@echo "  make run-secondary  Run secondary Spring Boot service"
	@echo "  make run-ui         Run Angular UI"
	@echo "  make test-demo      Smoke test primary -> secondary"
	@echo "  make destroy        Destroy Cognito stack"
	@echo "  make clean          Clean local build outputs"
	@echo "  make docs           Build the MkDocs site into ./site"
	@echo "  make docs-serve     Serve the docs locally with live reload"
	@echo "  make run-moto       Start a local moto server (fake AWS)"
	@echo "  make run-robotocore   Start robotocore container (real token endpoint)"
	@echo "  make stop-robotocore  Stop the robotocore container"
	@echo "  make cognito-setup  Provision Cognito via boto3 (moto by default)"
	@echo "  make cognito-status Show what Cognito resources exist"
	@echo "  make cognito-teardown  Delete the Cognito user pool"

install:
	./scripts/install.sh

deploy:
	./scripts/deploy-cognito.sh

export-env:
	./scripts/export-cdk-outputs.sh

run-primary:
	./scripts/run-primary.sh

run-secondary:
	./scripts/run-secondary.sh

run-ui:
	./scripts/run-ui.sh

test-demo:
	./scripts/test-demo.sh

destroy:
	./scripts/destroy-cognito.sh

clean:
	./scripts/clean.sh

docs:
	uv sync
	uv run mkdocs build --strict

docs-serve:
	uv sync
	uv run mkdocs serve

run-moto:
	./scripts/run-moto.sh

run-robotocore:
	./scripts/run-robotocore.sh

stop-robotocore:
	./scripts/stop-robotocore.sh

cognito-setup:
	uv run python scripts/cognito_setup.py setup --endpoint-url "$(COGNITO_ENDPOINT_URL)"

cognito-status:
	uv run python scripts/cognito_setup.py status --endpoint-url "$(COGNITO_ENDPOINT_URL)"

cognito-teardown:
	uv run python scripts/cognito_setup.py teardown --endpoint-url "$(COGNITO_ENDPOINT_URL)"
