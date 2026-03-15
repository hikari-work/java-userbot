package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.EditMessage;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.management.*;

@Slf4j
@Component
public class Ping {

    private final EditMessage editMessage;
    @Value("${user.id}")
    private Long userId;

    private final SimpleTelegramClient client;

    public Ping(EditMessage editMessage, @Qualifier("userBotClient") SimpleTelegramClient client) {
        this.editMessage = editMessage;
        this.client = client;
    }

    @Async
    @UserBotCommand(commands = {"p", "ping"}, description = "Cek Ping Server", sudoOnly = true)
    public void pingHandler(TdApi.UpdateNewMessage update, String args) {
        long chatId = update.message.chatId;
        long messageId = update.message.id;

        if (client.getMe().id != userId) return;

        long startTime = System.currentTimeMillis();

        String initialText = "<i>Pinging..</i>";

        editMessage.editMessage(chatId, messageId, initialText)
                .flatMap(ignored -> {
                    long totalTime = (System.currentTimeMillis() - startTime) / 4;
                    String finalStats = getRuntimeInformation(totalTime);
                    return editMessage.editMessage(chatId, messageId, finalStats);
                })
                .doOnError(error -> log.error("Failed to edit ping message: {}", error.getMessage()))
                .subscribe();
    }

    private String getRuntimeInformation(Long latency) {
        return String.format("<b>Pong!</b>\nLatency: <code>%dms</code>\n\n", latency) + getInternalRuntime();
    }

    private String getInternalRuntime() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        long uptimeSecs = runtime.getUptime() / 1000;
        long days = uptimeSecs / 86400;
        long hours = (uptimeSecs % 86400) / 3600;
        long mins = (uptimeSecs % 3600) / 60;
        long secs = uptimeSecs % 60;

        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMem = heapUsage.getUsed() / (1024 * 1024);
        long maxMem = heapUsage.getMax() / (1024 * 1024);

        double cpuLoad = osBean.getSystemLoadAverage() * 100;
        int threadCount = threadBean.getThreadCount();

        long youngUsed = 0, oldUsed = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName().toLowerCase();
            if (name.contains("eden") || name.contains("survivor")) {
                youngUsed += pool.getUsage().getUsed() / (1024 * 1024);
            } else if (name.contains("old") || name.contains("tenured")) {
                oldUsed += pool.getUsage().getUsed() / (1024 * 1024);
            }
        }

        StringBuilder gcInfo = new StringBuilder();
        long totalGcTime = 0;
        long totalGcCount = 0;

        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            long time = gc.getCollectionTime();
            if (count > 0) {
                totalGcCount += count;
                totalGcTime += time;
            }
            gcInfo.append(gc.getName()).append(" ");
        }
        long avgPause = (totalGcCount > 0) ? (totalGcTime / totalGcCount) : 0;

        return "<b>📊 Platform Status</b>\n" +
                "<blockquote expandable>" +
                "<pre>" +
                "├─ JVM Name   : " + runtime.getVmVendor() + " " + runtime.getSpecVendor() + "\n" +
                "├─ CPU Usage  : " + String.format("%.2f%%", cpuLoad) + "\n" +
                "├─ Uptime     : " + String.format("%dd %02d:%02d:%02d", days, hours, mins, secs) + "\n" +
                "├─ Threads    : " + threadCount + "\n" +
                "├─ Heap Used  : " + usedMem + " / " + maxMem + " MB\n" +
                "├─ Young Gen  : " + youngUsed + " MB\n" +
                "├─ Old Gen    : " + oldUsed + " MB\n" +
                "├─ GC Type    : " + gcInfo.toString().trim() + "\n" +
                "└─ GC Pause   : " + avgPause + " ms" +
                "</pre>" +
                "</blockquote>";
    }
}