.PHONY: install build test docs

install:
	@echo "Install target does nothing for this project"

build:
	./gradlew :lib:build

test:
	./gradlew :lib:test

docs:
	./gradlew :lib:dokkaJavadoc
	ln -sf lib/docs docs
