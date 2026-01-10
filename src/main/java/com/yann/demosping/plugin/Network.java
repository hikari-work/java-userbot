package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;

@Component
public class Network {

    private final SimpleTelegramClient client;
    private final SendMessageUtils sendMessageUtils; // Asumsi Anda punya utils ini

    public Network(@Qualifier("userBotClient") SimpleTelegramClient client,
                   SendMessageUtils sendMessageUtils) {
        this.client = client;
        this.sendMessageUtils = sendMessageUtils;
    }

    @UserBotCommand(commands = "net", description = "Check Network Usage", sudoOnly = true)
    public void logs(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;

        client.send(new TdApi.GetNetworkStatistics(false), net -> {
            if (net.isError()) {
                sendMessageUtils.sendMessage(chatId, "❌ Error: " + net.getError().message);
                return;
            }

            TdApi.NetworkStatistics stats = net.get();
            StringBuilder sb = new StringBuilder("<b>📊 Network Statistics</b>\n\n");

            long totalSent = 0;
            long totalReceived = 0;

            sb.append("<code>Type        |   Sent   |   Recv</code>\n");
            sb.append("<code>---------------------------------</code>\n");

            for (TdApi.NetworkStatisticsEntry entry : stats.entries) {
                String typeName = "Unknown";
                long sent = 0;
                long recv = 0;

                if (entry instanceof TdApi.NetworkStatisticsEntryFile fileStats) {
                    typeName = getFileTypeName(fileStats.fileType);
                    sent = fileStats.sentBytes;
                    recv = fileStats.receivedBytes;
                }
                else if (entry instanceof TdApi.NetworkStatisticsEntryCall callStats) {
                    typeName = "Calls";
                    sent = callStats.sentBytes;
                    recv = callStats.receivedBytes;
                }
                else if (entry instanceof TdApi.NetworkStatisticsEntryFile genericStats) {
                    typeName = "Generic";
                    sent = genericStats.sentBytes;
                    recv = genericStats.receivedBytes;
                }

                if (sent > 0 || recv > 0) {
                    totalSent += sent;
                    totalReceived += recv;
                    String line = String.format("%-11s | %8s | %8s",
                            truncate(typeName, 11),
                            formatSize(sent),
                            formatSize(recv));

                    sb.append("<code>").append(line).append("</code>\n");
                }
            }

            sb.append("<code>---------------------------------</code>\n");
            String totalLine = String.format("%-11s | %8s | %8s",
                    "TOTAL", formatSize(totalSent), formatSize(totalReceived));
            sb.append("<code>").append(totalLine).append("</code>");

            sendMessageUtils.sendMessage(chatId, sb.toString());
        });
    }


    /**
     * Mengubah object TdApi.FileType menjadi String nama yang mudah dibaca
     */
    private String getFileTypeName(TdApi.FileType fileType) {
        if (fileType instanceof TdApi.FileTypePhoto) return "Photos";
        if (fileType instanceof TdApi.FileTypeVideo) return "Videos";
        if (fileType instanceof TdApi.FileTypeDocument) return "Documents";
        if (fileType instanceof TdApi.FileTypeAudio) return "Music";
        if (fileType instanceof TdApi.FileTypeVoiceNote) return "Voice";
        if (fileType instanceof TdApi.FileTypeSticker) return "Stickers";
        if (fileType instanceof TdApi.FileTypeVideoNote) return "VidNotes";
        if (fileType instanceof TdApi.FileTypeAnimation) return "GIFs";
        if (fileType instanceof TdApi.FileTypeUnknown) return "Unknown";
        return "Other";
    }

    /**
     * Konversi bytes ke KB/MB/GB
     */
    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + "" + units[digitGroups];
    }

    private String truncate(String str, int width) {
        if (str.length() > width) {
            return str.substring(0, width);
        }
        return str;
    }
}