.PHONY: build test docs help install

# Default target
help:
	@echo "Available targets:"
	@echo "  build     - Build the solution"
	@echo "  test      - Run tests"
	@echo "  docs      - Build documentation"
	@echo "  lint      - Run linting"
	@echo "  help      - Show this help message"

install:
	dotnet restore

build:
	dotnet build LaunchDarkly.Observability.sln

test:
	dotnet test LaunchDarkly.Observability.sln

lint:
	dotnet format --verify-no-changes

docs:
	dotnet tool update -g docfx
	docfx metadata
	docfx build
