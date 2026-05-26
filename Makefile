.PHONY: build run

build:
	@./gradlew --quiet installDist

run:
	@test -x build/install/rusty/bin/rusty || { echo "Missing build/install/rusty/bin/rusty. Run 'make build' first." >&2; exit 1; }
	@build/install/rusty/bin/rusty --stdio-asm
