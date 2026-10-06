# Shortcuts for common tasks. Override on the command line, e.g. `make emulator AVD=Pixel_8`.
export JAVA_HOME ?= /opt/homebrew/opt/openjdk@17
SDK ?= /opt/homebrew/share/android-commandlinetools
AVD ?= gym
ADB := $(SDK)/platform-tools/adb

.PHONY: help build test device install run emulator emulator-fresh clean

help:  ## List targets
	@grep -E '^[a-z-]+:.*##' $(MAKEFILE_LIST) | sed 's/:.*##/\t/'

build:  ## Assemble the debug APK
	./gradlew :app:assembleDebug

test:  ## Run unit tests
	./gradlew :app:testDebugUnitTest

device:
	@$(ADB) devices | grep -q 'device$$' || $(MAKE) emulator

install: device  ## Build and install (starts the emulator if no device is connected)
	./gradlew :app:installDebug

run: install  ## Install and launch the app
	$(ADB) shell am start -n com.bar.gym/.MainActivity

emulator:  ## Start the emulator in the background and wait for boot
	$(SDK)/emulator/emulator -avd $(AVD) -no-audio -no-boot-anim >/dev/null 2>&1 &
	$(ADB) wait-for-device
	@until [ "$$($(ADB) shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done

emulator-fresh:  ## Like emulator, but headless and discards all changes on exit
	$(SDK)/emulator/emulator -avd $(AVD) -read-only -no-window -no-audio -no-boot-anim -no-snapshot >/dev/null 2>&1 &
	$(ADB) wait-for-device
	@until [ "$$($(ADB) shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done

clean:  ## Delete build output
	./gradlew clean
