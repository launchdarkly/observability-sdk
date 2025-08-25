.PHONY: build test docs help

# Default target
help:
	@echo "Available targets:"
	@echo "  build     - Build the solution"
	@echo "  test      - Run tests"
	@echo "  docs      - Build documentation"
	@echo "  help      - Show this help message"

build:
	dotnet restore
	dotnet build LaunchDarkly.Observability.sln

test:
	dotnet restore
	dotnet test LaunchDarkly.Observability.sln

docs:
	dotnet restore
	dotnet tool update -g docfx
	docfx metadata
	docfx build
