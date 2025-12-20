#!/bin/bash

JAR_NAME="userbot.jar"
PID_TO_KILL=$1

echo "Menunggu bot lama mati..."
sleep 5

# Pastikan benar-benar mati
if [ -n "$PID_TO_KILL" ]; then
    kill -9 $PID_TO_KILL 2>/dev/null
fi
pkill -f $JAR_NAME

echo "Menjalankan bot baru..."
# Koreksi typo path '/taget/' menjadi 'target/' dan fix heap size options
nohup java -Xmx128m -Xmn48m -XX:+UseG1GC -jar target/$JAR_NAME >> bot_runtime.log 2>&1 &