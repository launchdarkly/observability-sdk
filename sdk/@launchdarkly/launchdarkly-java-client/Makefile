.PHONY: install build test

install:
	@echo "Install target does nothing for this project"

build:
	cd plugin && ./gradlew check javadoc
	cd demo && ./gradlew build

test:
	cd plugin && ./gradlew test
