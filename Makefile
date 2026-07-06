.PHONY: help install deploy destroy export-env run-primary run-secondary run-ui test-demo clean docs docs-serve

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
	python -m pip install -q -r docs/requirements.txt
	mkdocs build --strict

docs-serve:
	python -m pip install -q -r docs/requirements.txt
	mkdocs serve
