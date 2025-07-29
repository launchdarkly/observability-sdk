.PHONY: help install lint test build clean format

# Default target
help:
	@echo "Available targets:"
	@echo "  install  - Install dependencies using go mod"
	@echo "  lint     - Run golangci-lint"
	@echo "  format   - Format code using gofmt"
	@echo "  test     - Run tests with coverage"
	@echo "  build    - Build the package"
	@echo "  clean    - Clean build artifacts"
	@echo "  all      - Run install, lint, test, and build"

# Install dependencies
install:
	go mod download
	go mod tidy

# Run linting with golangci-lint
lint:
	golangci-lint run

# Format code with gofmt
format:
	gofmt -s -w .

# Run tests with coverage
test:
	go test -v -race -coverprofile=coverage.out -covermode=atomic ./...
	go tool cover -html=coverage.out -o coverage.html
	go tool cover -func=coverage.out

# Build the package
build:
	go build -v ./...

# Clean build artifacts
clean:
	go clean
	rm -f coverage.out coverage.html

# Run all checks (install, lint, test, build)
all: install lint test build 