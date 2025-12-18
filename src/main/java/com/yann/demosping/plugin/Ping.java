package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.management.*;
import java.util.List;

@Component
@RequiredArgsConstructor
public class Ping {

    @Value("${user.id}")
    private Long userId;

    private final SimpleTelegramClient client;

    @Async
    @UserBotCommand(commands = {"p", "ping"}, description = "Cek Ping Server")
    public void pingHandler(TdApi.UpdateNewMessage update, String args) {
        long chatId = update.message.chatId;
        long messageId = update.message.id;
        if (client.getMe().id != userId) return;
        long startTime = System.currentTimeMillis();


        try {
            String initialText = "<i>Pinging..</i>";
            client.send(new TdApi.ParseTextEntities(initialText, new TdApi.TextParseModeHTML()), parseResult -> {
                if (parseResult.isError()) return;

                TdApi.FormattedText initialFmt = parseResult.get();

                client.send(new TdApi.EditMessageText(chatId, messageId, null,
                        new TdApi.InputMessageText(initialFmt, new TdApi.LinkPreviewOptions(), false)
                ), sentResult -> {
                    if (!sentResult.isError()) {
                        long totalTime = (System.currentTimeMillis() - startTime) / 4;
                        String finalStats = getRuntimeInformation(totalTime);

                        client.send(
                                new TdApi.ParseTextEntities(finalStats,
                                        new TdApi.TextParseModeHTML()), finalParseResult -> {
                            if (finalParseResult.isError()) return;

                            TdApi.FormattedText finalFmt = finalParseResult.get();
                            client.send(new TdApi.EditMessageText(
                                    chatId, messageId, null,
                                    new TdApi.InputMessageText(finalFmt, new TdApi.LinkPreviewOptions(), false)
                            ));
                        });
                    }
                });
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private String getRuntimeInformation(Long latency) {
        return String.format("<b>Pong!</b>\nLatency: <code>%dms</code>\n\n", latency) + getInternalRuntime();
    }
    private String getInternalRuntime() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        // Uptime Calculation
        long uptimeSecs = runtime.getUptime() / 1000;
        long days = uptimeSecs / 86400;
        long hours = (uptimeSecs % 86400) / 3600;
        long mins = (uptimeSecs % 3600) / 60;
        long secs = uptimeSecs % 60;

        // Memory Usage
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long usedMem = heapUsage.getUsed() / (1024 * 1024);
        long maxMem = heapUsage.getMax() / (1024 * 1024);

        // CPU & Threads
        double cpuLoad = osBean.getSystemLoadAverage() * 100;
        int threadCount = threadBean.getThreadCount();

        // GC & Memory Pools (Young/Old Gen)
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
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            totalGcTime += gc.getCollectionTime();
            gcInfo.append(gc.getName()).append(" ");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<b>📊 Platform Status</b>\n");
        builder.append("<blockquote expandable>");
        builder.append("<pre>");
        builder.append("├─ JVM Name   : ").append(runtime.getVmVendor()).append(" ").append(runtime.getVmName()).append("\n");
        builder.append("├─ CPU Usage  : ").append(String.format("%.2f%%", cpuLoad)).append("\n");
        builder.append("├─ Uptime     : ").append(String.format("%dd %02d:%02d:%02d", days, hours, mins, secs)).append("\n");
        builder.append("├─ Threads    : ").append(threadCount).append("\n");
        builder.append("├─ Heap Used  : ").append(usedMem).append(" / ").append(maxMem).append(" MB\n");
        builder.append("├─ Young Gen  : ").append(youngUsed).append(" MB\n");
        builder.append("├─ Old Gen    : ").append(oldUsed).append(" MB\n");
        builder.append("├─ GC Type    : ").append(gcInfo.toString().trim()).append("\n");
        builder.append("└─ GC Pause   : ").append(totalGcTime / ManagementFactory.getGarbageCollectorMXBeans().size()).append(" ms");
        builder.append("</pre>");
        builder.append("</blockquote>");

        return builder.toString();
    }
}