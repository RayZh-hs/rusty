.PHONY: build run

build:
	@test -f build/install/rusty/bin/rusty || { echo "Pre-built artifacts missing. Run './gradlew installDist' locally before submitting." >&2; exit 1; }

run:
	@test -x build/install/rusty/bin/rusty || { echo "Missing build/install/rusty/bin/rusty. Run 'make build' first." >&2; exit 1; }
	@build/install/rusty/bin/rusty --stdio-asm
