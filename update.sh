#!/bin/bash

exec > >(tee -i update_debug.log)
exec 2>&1

echo "=== START UPDATE SCRIPT ==="
echo "Date: $(date)"
JAVA_CMD="/home/viandra-stefani/.sdkman/candidates/java/current/bin/java"
WORK_DIR="/home/viandra-stefani/java-userbot"
JAR_NAME="userbot.jar"

cd $WORK_DIR


OLD_PID=$1
if [ -n "$OLD_PID" ]; then
    echo "Killing Old PID: $OLD_PID"
    kill -9 $OLD_PID 2>/dev/null
fi


sleep 3
pkill -f $JAR_NAME
if [ -f "target/$JAR_NAME" ]; then
    echo "Starting new bot..."
    setsid $JAVA_CMD -Xmx128m -jar target/$JAR_NAME > bot_runtime.log 2>&1 &
    echo "New bot launched."
else
    echo "CRITICAL: File target/$JAR_NAME not found inside $WORK_DIR"
fi