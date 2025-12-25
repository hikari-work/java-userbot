#!/bin/bash

exec > >(tee -i update_debug.log)
exec 2>&1

echo "=== START UPDATE SCRIPT ==="
echo "Date: $(date)"

JAVA_CMD="/root/.sdkman/candidates/java/current/bin/java"

WORK_DIR="/root/java-userbot"

JAR_NAME="userbot.jar"

if [ -d "$WORK_DIR" ]; then
    cd $WORK_DIR
else
    echo "CRITICAL: Directory $WORK_DIR not found!"
    exit 1
fi

OLD_PID=$1
if [ -n "$OLD_PID" ]; then
    echo "Killing Old PID: $OLD_PID"
    kill -9 $OLD_PID 2>/dev/null
fi

sleep 3
pkill -f $JAR_NAME

if [ -f "target/$JAR_NAME" ]; then
    echo "Starting new bot..."
    if [ -x "$JAVA_CMD" ]; then
        setsid $JAVA_CMD -XX:+UseZGC -Xmx128m -Xss512k -XX:MaxMetaspaceSize=128m -jar target/$JAR_NAME > bot_runtime.log 2>&1 &
        echo "New bot launched."
    else
        echo "CRITICAL: Java command not found at $JAVA_CMD"
    fi
else
    echo "CRITICAL: File target/$JAR_NAME not found inside $WORK_DIR"
fi