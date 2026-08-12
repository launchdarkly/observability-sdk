.PHONY: install build test docs

install:
	@echo "Install target does nothing for this project"

build:
	./gradlew :otel:build :lib:build

test:
	./gradlew :otel:test :lib:test

docs:
	./gradlew :lib:dokkaGeneratePublicationJavadoc
	ln -sf lib/docs docs
