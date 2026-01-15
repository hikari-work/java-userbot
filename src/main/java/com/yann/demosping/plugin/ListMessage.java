package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.service.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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

        String link = param.get("f");
        String prefix = param.getOrDefault("p", "");
        String suffix = param.getOrDefault("s", "");
        boolean mergeMode = param.containsKey("m");

        if ("true".equals(suffix) && param.containsKey("e")) {
            suffix = "-e";
            param.remove("e");
        }

        int count = 1;
        try {
            if (param.get("c") != null) count = Integer.parseInt(param.get("c"));
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

            log.info("🚀 [ListMessage] Generating {} links from {} (merge: {})", finalCount, link, mergeMode);

            if (mergeMode) {
                generateMergedLinks(currentChatId, baseUrl, startMessageId, finalCount, prefix, suffix, commandMsgId);
            } else {
                sendNextLink(currentChatId, baseUrl, startMessageId, finalCount, prefix, suffix, 0, commandMsgId);
            }

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

    private void generateMergedLinks(long targetChatId, String baseUrl, long startId, int count,
                                     String prefix, String suffix, long commandMsgId) {
        StringBuilder allLinks = new StringBuilder();

        for (int i = 0; i < count; i++) {
            long currentId = startId + i;
            String generatedLink = baseUrl + "/" + currentId;

            if (!prefix.isEmpty()) {
                allLinks.append(prefix).append(" ");
            }
            allLinks.append(generatedLink);
            if (!suffix.isEmpty()) {
                allLinks.append(" ").append(suffix);
            }

            if (i < count - 1) {
                allLinks.append("\n");
            }
        }

        String finalText = allLinks.toString();

        // Cek apakah panjangnya melebihi 2048 karakter
        if (finalText.length() > 2048) {
            log.info("📄 [ListMessage] Text too long ({}), sending as file", finalText.length());
            sendAsFile(targetChatId, finalText, commandMsgId, count);
        } else {
            log.info("💬 [ListMessage] Sending merged links as single message");
            sendAsSingleMessage(targetChatId, finalText, commandMsgId, count);
        }
    }

    private void sendAsSingleMessage(long targetChatId, String text, long commandMsgId, int totalLinks) {
        TdApi.InputMessageContent content = new TdApi.InputMessageText(
                new TdApi.FormattedText(text, new TdApi.TextEntity[0]),
                new TdApi.LinkPreviewOptions(),
                false
        );

        client.send(new TdApi.SendMessage(targetChatId, 0, null, null, null, content), result -> {
            if (result.isError()) {
                log.error("Failed to send merged message: {}", result.getError().message);
            } else {
                log.info("✅ [ListMessage] Merged message sent with {} links", totalLinks);
            }
            deleteCommandMessage(targetChatId, commandMsgId);
        });
    }

    private void sendAsFile(long targetChatId, String text, long commandMsgId, int totalLinks) {
        try {
            byte[] fileBytes = text.getBytes(StandardCharsets.UTF_8);

            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal(
                    saveToTempFile(fileBytes, "links.txt")
            );

            TdApi.InputMessageDocument documentContent = new TdApi.InputMessageDocument(
                    inputFile,
                    null,
                    false,
                    new TdApi.FormattedText("📝 Generated " + totalLinks + " links", new TdApi.TextEntity[0])
            );

            client.send(new TdApi.SendMessage(targetChatId, 0, null, null, null, documentContent), result -> {
                if (result.isError()) {
                    log.error("Failed to send file: {}", result.getError().message);
                } else {
                    log.info("✅ [ListMessage] File sent with {} links", totalLinks);
                }
                deleteCommandMessage(targetChatId, commandMsgId);
            });

        } catch (Exception e) {
            log.error("Error creating file: {}", e.getMessage());
            deleteCommandMessage(targetChatId, commandMsgId);
        }
    }

    private String saveToTempFile(byte[] data, String filename) throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("telegram_links_", "_" + filename);
        tempFile.deleteOnExit();

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            fos.write(data);
        }

        return tempFile.getAbsolutePath();
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
        deleteCommandMessage(chatId, commandMsgId);
        log.info("✅ [ListMessage] Process finished. Total sent: {}", totalSent);
    }

    private void deleteCommandMessage(long chatId, long commandMsgId) {
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
                log.info("✅ [ListMessage] Command message deleted");
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
                <code>%slist -f [LINK] -c [NUM] -p [TEXT] -s [TEXT] -m</code>
                
                <b>Parameter:</b>
                • <code>-f</code> : Link awal yang akan di-generate (wajib)
                • <code>-c</code> : Jumlah link yang akan dibuat (default: 1, max: 100)
                • <code>-p</code> : Text di awal link (opsional)
                • <code>-s</code> : Text di akhir link (opsional)
                • <code>-m</code> : Gabungkan semua link dalam 1 pesan (opsional)
                
                <b>Contoh Penggunaan:</b>
                
                1️⃣ <b>Basic (tanpa prefix/suffix):</b>
                <code>%slist -f https://t.me/c/1234567890/100 -c 3</code>
                
                <i>Hasil (3 pesan terpisah):</i>
                <code>https://t.me/c/1234567890/100
                https://t.me/c/1234567890/101
                https://t.me/c/1234567890/102</code>
                
                2️⃣ <b>Dengan Merge (1 pesan):</b>
                <code>%slist -f https://t.me/c/1234567890/100 -c 3 -m</code>
                
                <i>Hasil (1 pesan):</i>
                <code>https://t.me/c/1234567890/100
                https://t.me/c/1234567890/101
                https://t.me/c/1234567890/102</code>
                
                3️⃣ <b>Merge dengan Prefix & Suffix:</b>
                <code>%slist -f https://t.me/c/1234567890/100 -c 3 -m -p 📢 -s ✨</code>
                
                <i>Hasil (1 pesan):</i>
                <code>📢 https://t.me/c/1234567890/100 ✨
                📢 https://t.me/c/1234567890/101 ✨
                📢 https://t.me/c/1234567890/102 ✨</code>
                
                <b>⚠️ Catatan:</b>
                • Tanpa <code>-m</code>: Delay antar message 2 detik
                • Dengan <code>-m</code>: Semua link dalam 1 pesan
                • Jika text melebihi 2048 karakter, akan dikirim sebagai file
                • Link harus berakhiran angka ID yang valid
                • Command message akan otomatis terhapus setelah selesai
                """.formatted(messagePrefix, messagePrefix, messagePrefix);

        sendMessageUtils.sendMessage(chatId, replyToMsgId, helpText)
                .exceptionally(ex -> {
                    log.error("Failed to send help message: {}", ex.getMessage());
                    return null;
                });
    }
}