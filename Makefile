.PHONY: build run

SOURCES := $(shell find src/main/kotlin vendor -name "*.kt" | sort)

build:
	mkdir -p build/classes
	kotlinc $(SOURCES) -d build/classes

run:
	@kotlin -J-XX:-UsePerfData -cp build/classes rusty.MainKt --stdio-asm
