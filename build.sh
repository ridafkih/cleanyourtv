#!/bin/sh
set -eu
SRC=/Users/villager/cleanyourtv
OUT=${1:-/Users/villager/.claude/jobs/ada0fe98/tmp/cyt-r1.apk}
rsync -a --delete --exclude .git --exclude launcher/app/build --exclude launcher/.gradle "$SRC/" mini-celebi:~/tmp/cyt-build/
ssh mini-celebi "export ANDROID_HOME=\$HOME/Library/Android/sdk; export JAVA_HOME=/opt/homebrew/opt/openjdk@17; export PATH=\$JAVA_HOME/bin:\$PATH; cd ~/tmp/cyt-build/launcher && ./gradlew assembleRelease --no-daemon"
scp mini-celebi:~/tmp/cyt-build/launcher/app/build/outputs/apk/release/app-release.apk "$OUT"
