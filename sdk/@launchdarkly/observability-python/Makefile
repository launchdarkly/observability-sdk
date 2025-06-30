.PHONY: help install lint test build clean format

# Default target
help:
	@echo "Available targets:"
	@echo "  install  - Install dependencies using poetry"
	@echo "  lint     - Run black code formatter check"
	@echo "  format   - Format code using black"
	@echo "  test     - Run pytest with coverage"
	@echo "  build    - Build the package using poetry"
	@echo "  clean    - Clean build artifacts"
	@echo "  all      - Run install, lint, test, and build"

# Install dependencies
install:
	poetry install --all-extras

# Run linting with black
lint:
	poetry run black --check .

# Format code with black
format:
	poetry run black .

# Run tests with coverage
test:
	poetry run pytest --cov=ldobserve --cov-branch --cov-report xml

# Build the package
build:
	poetry build

# Clean build artifacts
clean:
	rm -rf dist/
	rm -rf .coverage
	rm -f coverage.xml

# Run all checks (install, lint, test, build)
all: install lint test build 