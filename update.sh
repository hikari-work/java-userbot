#!/bin/bash

# Arahkan semua output ke log agar ketahuan errornya
exec > >(tee -i update_debug.log)
exec 2>&1

echo "=== START UPDATE SCRIPT ==="
echo "Date: $(date)"
JAVA_CMD="/home/viandra-stefani/.sdkman/candidates/java/current/bin/java"  # Hasil dari 'which java'
WORK_DIR="/home/viandra-stefani/java-userbot" # Hasil dari 'pwd' di folder project
JAR_NAME="userbot.jar"

cd $WORK_DIR

# 2. Kill PID Lama
OLD_PID=$1
if [ -n "$OLD_PID" ]; then
    echo "Killing Old PID: $OLD_PID"
    kill -9 $OLD_PID 2>/dev/null
fi

# Tunggu proses benar-benar hilang
sleep 3
pkill -f $JAR_NAME

# 3. Jalankan Baru dengan Path Lengkap
if [ -f "target/$JAR_NAME" ]; then
    echo "Starting new bot..."
    # 'setsid' di sini juga membantu
    setsid $JAVA_CMD -Xmx256m -jar target/$JAR_NAME > bot_runtime.log 2>&1 &
    echo "New bot launched."
else
    echo "CRITICAL: File target/$JAR_NAME not found inside $WORK_DIR"
fi