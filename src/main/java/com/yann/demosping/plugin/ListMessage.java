package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.utils.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ListMessage {

    private final SimpleTelegramClient client;
    private final SendMessageUtils sendMessageUtils;

    @Value("${message.prefix}")
    private String messagePrefix;

    public ListMessage(@Qualifier("userBotClient") SimpleTelegramClient client,
                       SendMessageUtils sendMessageUtils) {
        this.client = client;
        this.sendMessageUtils = sendMessageUtils;
    }

    @UserBotCommand(commands = {"list", "lst"}, description = "Generate links purely by math (No API check)")
    public void generateMessageLinks(TdApi.UpdateNewMessage message, String args) {
        long currentChatId = message.message.chatId;
        long commandMsgId = message.message.id;

        Map<String, String> param = ArgsParser.parse(args);

        if (param.containsKey("help") || args.trim().equals("-help") || args.trim().equals("--help")) {
            sendHelpMessage(currentChatId, commandMsgId);
            return;
        }

        String link = param.get("from");
        String prefix = param.getOrDefault("prefix", "");
        String suffix = param.getOrDefault("suffix", "");

        int count = 1;
        try {
            if (param.get("count") != null) count = Integer.parseInt(param.get("count"));
        } catch (NumberFormatException ignored) {}

        if (count > 100) count = 100;
        final int finalCount = count;

        if (link == null || link.isEmpty()) {
            sendMessageUtils.sendMessage(currentChatId, commandMsgId,
                            "❌ Link required. Use <code>" + messagePrefix + "list -help</code> for tutorial.")
                    .exceptionally(ex -> {
                        log.error("Failed to send error message: {}", ex.getMessage());
                        return null;
                    });
            return;
        }

        try {
            if (link.endsWith("/")) {
                link = link.substring(0, link.length() - 1);
            }

            int lastSlashIndex = link.lastIndexOf('/');
            if (lastSlashIndex == -1 || lastSlashIndex == link.length() - 1) {
                sendMessageUtils.sendMessage(currentChatId, commandMsgId, "❌ Format link salah. Harus berakhiran angka ID.")
                        .exceptionally(ex -> {
                            log.error("Failed to send error message: {}", ex.getMessage());
                            return null;
                        });
                return;
            }

            String baseUrl = link.substring(0, lastSlashIndex);
            String idString = link.substring(lastSlashIndex + 1);

            long startMessageId = Long.parseLong(idString);

            // Langsung kirim link tanpa status message
            log.info("🚀 [ListMessage] Generating {} links from {}", finalCount, link);
            sendNextLink(currentChatId, baseUrl, startMessageId, finalCount, prefix, suffix, 0, commandMsgId);

        } catch (NumberFormatException e) {
            sendMessageUtils.sendMessage(currentChatId, commandMsgId, "❌ ID di akhir link bukan angka valid.")
                    .exceptionally(ex -> {
                        log.error("Failed to send error message: {}", ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            sendMessageUtils.sendMessage(currentChatId, commandMsgId, "❌ Error parsing link: " + e.getMessage())
                    .exceptionally(ex -> {
                        log.error("Failed to send error message: {}", ex.getMessage());
                        return null;
                    });
        }
    }

    private void sendNextLink(long targetChatId, String baseUrl, long currentId, int remainingCount,
                              String prefix, String suffix, int sentCount, long commandMsgId) {

        if (remainingCount <= 0) {
            log.info("🏁 [ListMessage] Loop selesai. Target Chat: {}, Sent: {}", targetChatId, sentCount);
            finishProcess(targetChatId, commandMsgId, sentCount);
            return;
        }

        String generatedLink = baseUrl + "/" + currentId;

        StringBuilder textBuilder = new StringBuilder();
        if (!prefix.isEmpty()) {
            textBuilder.append(prefix).append(" ");
        }
        textBuilder.append(generatedLink);
        if (!suffix.isEmpty()) {
            textBuilder.append(" ").append(suffix);
        }

        String textToSend = textBuilder.toString();

        TdApi.InputMessageContent content = new TdApi.InputMessageText(
                new TdApi.FormattedText(textToSend, new TdApi.TextEntity[0]),
                new TdApi.LinkPreviewOptions(),
                false
        );

        client.send(new TdApi.SendMessage(targetChatId, 0, null, null, null, content), result -> {
            if (result.isError()) {
                log.error("Failed to send link {}: {}", generatedLink, result.getError().message);
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            sendNextLink(targetChatId, baseUrl, currentId + 1, remainingCount - 1, prefix, suffix, sentCount + 1, commandMsgId);
        });
    }

    private void finishProcess(long chatId, long commandMsgId, int totalSent) {
        log.info("🗑️ [ListMessage] Deleting command message ({})", commandMsgId);

        client.send(new TdApi.DeleteMessages(chatId, new long[]{commandMsgId}, true), result -> {
            if (result.isError()) {
                String errorMsg = result.getError().message;
                if (errorMsg.contains("Message not found") || errorMsg.contains("MESSAGE_ID_INVALID")) {
                    log.debug("Command message already deleted: {}", commandMsgId);
                } else {
                    log.error("❌ [ListMessage] Delete failed: {}", errorMsg);
                }
            } else {
                log.info("✅ [ListMessage] Command message deleted. Total sent: {}", totalSent);
            }
        });
    }

    /**
     * Kirim tutorial/help message
     */
    private void sendHelpMessage(long chatId, long replyToMsgId) {
        String helpText = """
                <b>📚 List Message Generator - Tutorial</b>
                
                <b>Deskripsi:</b>
                Generate multiple message links secara otomatis berdasarkan perhitungan matematis (tanpa API check).
                
                <b>Format Command:</b>
                <code>%slist -from [LINK] -count [NUM] -prefix [TEXT] -suffix [TEXT]</code>
                
                <b>Parameter:</b>
                • <code>-from</code> : Link awal yang akan di-generate (wajib)
                • <code>-count</code> : Jumlah link yang akan dibuat (default: 1, max: 100)
                • <code>-prefix</code> : Text di awal link (opsional)
                • <code>-suffix</code> : Text di akhir link (opsional)
                
                <b>Contoh Penggunaan:</b>
                
                1️⃣ <b>Basic (tanpa prefix/suffix):</b>
                <code>%slist -from https://t.me/c/1234567890/100 -count 3</code>
                
                <i>Hasil:</i>
                <code>https://t.me/c/1234567890/100
                https://t.me/c/1234567890/101
                https://t.me/c/1234567890/102</code>
                
                2️⃣ <b>Dengan Prefix:</b>
                <code>%slist -from https://t.me/c/1234567890/100 -count 3 -prefix 📢 NEW:</code>
                
                <i>Hasil:</i>
                <code>📢 NEW: https://t.me/c/1234567890/100
                📢 NEW: https://t.me/c/1234567890/101
                📢 NEW: https://t.me/c/1234567890/102</code>
                
                3️⃣ <b>Dengan Prefix & Suffix:</b>
                <code>%slist -from https://t.me/c/1234567890/100 -count 3 -prefix 📢 -suffix ✨</code>
                
                <i>Hasil:</i>
                <code>📢 https://t.me/c/1234567890/100 ✨
                📢 https://t.me/c/1234567890/101 ✨
                📢 https://t.me/c/1234567890/102 ✨</code>
                
                <b>⚠️ Catatan:</b>
                • Delay antar message: 2 detik
                • Link harus berakhiran angka ID yang valid
                • Command message akan otomatis terhapus setelah selesai
                """.formatted(messagePrefix, messagePrefix, messagePrefix, messagePrefix);

        sendMessageUtils.sendMessage(chatId, replyToMsgId, helpText)
                .exceptionally(ex -> {
                    log.error("Failed to send help message: {}", ex.getMessage());
                    return null;
                });
    }
}