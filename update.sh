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
    echo "Exploding JAR for JShell classpath compatibility..."
    rm -rf target/exploded
    mkdir -p target/exploded
    cd target/exploded && jar xf "../$JAR_NAME" && cd "$WORK_DIR"

    MAIN_CLASS=$(unzip -p "target/$JAR_NAME" BOOT-INF/classes/META-INF/MANIFEST.MF 2>/dev/null | grep "Start-Class" | awk '{print $2}' | tr -d '\r')
    if [ -z "$MAIN_CLASS" ]; then
        MAIN_CLASS=$(unzip -p "target/$JAR_NAME" META-INF/MANIFEST.MF 2>/dev/null | grep "Start-Class" | awk '{print $2}' | tr -d '\r')
    fi

    echo "Starting new bot (exploded, main=$MAIN_CLASS)..."
    if [ -x "$JAVA_CMD" ]; then
        setsid $JAVA_CMD -XX:+UseG1GC -Xmx128m -Xms64m -Xss256k -XX:MaxMetaspaceSize=256m \
            -cp "target/exploded/BOOT-INF/classes:target/exploded/BOOT-INF/lib/*" \
            $MAIN_CLASS --server.port=9003 > bot_runtime.log 2>&1 &
        echo "New bot launched."
    else
        echo "CRITICAL: Java command not found at $JAVA_CMD"
    fi
else
    echo "CRITICAL: File target/$JAR_NAME not found inside $WORK_DIR"
fi
