.PHONY: help install lint test build clean format

# Default target
help:
	@echo "Available targets:"
	@echo "  install  - Clean installs using poetry"
	@echo "  run      - Run using poetry"

# This is a pretty aggressive install that removes all the site packages.
# This ensures that we get the latest version when working on changes to the plugin.
install:
	find $(poetry env info --path) -type d -name site-packages -exec rm -rf {} \; ; poetry lock && poetry install

run:
	poetry run flask run
