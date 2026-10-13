# AntennaPod (fork) — build and flash the play-debug app.
#
# Default phone is the Galaxy S21 Ultra (SM-G998B, arm64-v8a), serial
# R3CRA0J9ANY, the same handset navio/frontend targets with
# `yarn android:boot --serial R3CRA0J9ANY`. Override: make flash SERIAL=<adb id>
#
# JDK 21 is required. JAVA_HOME is pinned so a JDK 17 Gradle daemon is not used.

JAVA_HOME := /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
export JAVA_HOME

SERIAL ?= R3CRA0J9ANY
export ANDROID_SERIAL := $(SERIAL)

ADB      := adb -s $(SERIAL)
GRADLE   := ./gradlew --console=plain
PKG      := xyz.liland.antennapod.debug
ACTIVITY := de.danoeh.antennapod.activity.SplashActivity
SHOT     := /tmp/antennapod-s21.png

.PHONY: help check-device build install flash run boot logs screenshot uidump devices uninstall clean

help:
	@echo "make flash      - build, install, and launch on $(SERIAL)"
	@echo "make build      - compile the play-debug variant"
	@echo "make install    - build and install, do not launch"
	@echo "make run        - same as flash"
	@echo "make logs       - recent logcat lines for this app"
	@echo "make screenshot - save a PNG to $(SHOT)"
	@echo "make uidump     - print the uiautomator hierarchy"
	@echo "make devices    - list connected adb devices"
	@echo "make uninstall  - remove the debug app"
	@echo "make clean      - clean the gradle build"
	@echo "SERIAL=<adb id> overrides the default S21 serial"

check-device:
	@adb devices | awk 'NR>1 && $$1=="$(SERIAL)" && $$2=="device" { found=1 } END { exit found ? 0 : 1 }' \
		|| { echo "Phone $(SERIAL) is not attached and authorized. Run: make devices"; exit 1; }

build:
	$(GRADLE) :app:assembleDebug

install: check-device
	$(GRADLE) :app:installPlayDebug

flash: install
	$(ADB) shell am force-stop $(PKG) || true
	$(ADB) shell am start -n $(PKG)/$(ACTIVITY)

run: flash

boot: flash

logs:
	$(ADB) logcat -d -t 200 | grep -E "$(PKG)|de.danoeh.antennapod" || true

screenshot: check-device
	$(ADB) exec-out screencap -p > $(SHOT)
	@echo $(SHOT)

uidump: check-device
	$(ADB) shell uiautomator dump /sdcard/ui.xml
	$(ADB) shell cat /sdcard/ui.xml

devices:
	adb devices -l

uninstall: check-device
	$(ADB) uninstall $(PKG)

clean:
	$(GRADLE) clean
