package com.yann.demosping.plugin.coomer;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.ParseTextEntitiesUtils;
import com.yann.demosping.service.SendMessageUtils;
import com.yann.demosping.utils.ArgsParser;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CoomerPlugin {

    private final CoomerService coomerService;
    private final SendMessageUtils sendMessageUtils;
    private final SimpleTelegramClient userBotClient;

    private static final Set<String> PHOTO_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".avi", ".mov", ".mkv", ".webm", ".flv", ".wmv", ".m4v");
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;

    public CoomerPlugin(CoomerService coomerService, SendMessageUtils sendMessageUtils, SimpleTelegramClient userBotClient, ParseTextEntitiesUtils parseTextEntitiesUtils) {
        this.coomerService = coomerService;
        this.sendMessageUtils = sendMessageUtils;
        this.userBotClient = userBotClient;
        this.parseTextEntitiesUtils = parseTextEntitiesUtils;
    }

    @UserBotCommand(commands = {"coomer"}, description = "Scrape media from coomer.st. Flags: -s (service) -u (userId) -p (pagination) -photo (only photos) -video (only videos)", sudoOnly = false)
    public void coomer(TdApi.UpdateNewMessage update, String args) {
        long chatId = update.message.chatId;
        Map<String, String> parse = ArgsParser.parse(args);

        String service = parse.getOrDefault("s", null);
        String userId = parse.getOrDefault("u", null);
        String pFlag = parse.getOrDefault("p", null); // Flag pagination
        boolean photoOnly = parse.containsKey("photo");
        boolean videoOnly = parse.containsKey("video");

        if (service == null || userId == null) {
            sendText(chatId, "❌ Error: Service (-s) atau UserID (-u) tidak ditemukan.\n\n" +
                    "Contoh penggunaan:\n" +
                    "`/coomer -s onlyfans -u username`\n" +
                    "`/coomer -s onlyfans -u username -photo`\n" +
                    "`/coomer -s onlyfans -u username -video`\n" +
                    "`/coomer -s onlyfans -u username -p 50`");
            return;
        }

        log.info("Scraping media - service: {}, userId: {}, photoOnly: {}, videoOnly: {}",
                service, userId, photoOnly, videoOnly);

        coomerService.scrapeAllMedia(service, userId)
                .subscribe(linksSet -> {
                    List<String> fullLinks = linksSet.stream()
                            .map(link -> "https://coomer.st" + link)
                            .collect(Collectors.toList());

                    if (fullLinks.isEmpty()) {
                        sendText(chatId, "❌ Tidak ditemukan media untuk user tersebut.");
                        return;
                    }

                    // Filter berdasarkan tipe media
                    List<String> filteredLinks = filterMediaByType(fullLinks, photoOnly, videoOnly);

                    if (filteredLinks.isEmpty()) {
                        String mediaType = photoOnly && videoOnly ? "media" :
                                photoOnly ? "foto" :
                                        videoOnly ? "video" : "media";
                        sendText(chatId, "❌ Tidak ditemukan " + mediaType + " untuk user tersebut.");
                        return;
                    }

                    // Sort links
                    filteredLinks.sort(String::compareTo);

                    // Info summary
                    String summaryInfo = buildSummaryInfo(fullLinks, filteredLinks, photoOnly, videoOnly);
                    sendText(chatId, summaryInfo);

                    // Pagination
                    List<List<String>> partitions = new ArrayList<>();
                    if (pFlag != null) {
                        try {
                            int batchSize = Integer.parseInt(pFlag);
                            for (int i = 0; i < filteredLinks.size(); i += batchSize) {
                                partitions.add(filteredLinks.subList(i, Math.min(i + batchSize, filteredLinks.size())));
                            }
                        } catch (NumberFormatException e) {
                            sendText(chatId, "❌ Error: Flag -p harus berupa angka.");
                            return;
                        }
                    } else {
                        partitions.add(filteredLinks);
                    }

                    int batchNumber = 1;
                    for (List<String> batch : partitions) {
                        String messageBody = String.join("\n", batch);
                        String batchInfo = partitions.size() > 1 ?
                                String.format("\n\n📦 Batch %d/%d", batchNumber, partitions.size()) : "";

                        if (messageBody.length() > 2048) {
                            String fileName = String.format("%s_%s_links_batch_%d.txt", service, userId, batchNumber);
                            sendFile(chatId, messageBody, fileName);
                        } else {
                            sendText(chatId, messageBody + batchInfo);
                        }
                        batchNumber++;
                    }

                    sendText(chatId, String.format("✅ Selesai! Total %d link dikirim.", filteredLinks.size()));

                }, error -> {
                    log.error("Error scraping media for service: {}, userId: {}", service, userId, error);
                    sendText(chatId, "❌ Terjadi kesalahan saat mengambil data.\nError: " + error.getMessage());
                });
    }

    /**
     * Filter links berdasarkan tipe media (photo/video)
     */
    private List<String> filterMediaByType(List<String> links, boolean photoOnly, boolean videoOnly) {
        if ((photoOnly && videoOnly) || (!photoOnly && !videoOnly)) {
            return links;
        }

        return links.stream()
                .filter(link -> {
                    String lowerLink = link.toLowerCase();

                    if (photoOnly) {
                        return PHOTO_EXTENSIONS.stream().anyMatch(lowerLink::endsWith);
                    }

                    if (videoOnly) {
                        return VIDEO_EXTENSIONS.stream().anyMatch(lowerLink::endsWith);
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * Build summary info message
     */
    private String buildSummaryInfo(List<String> allLinks, List<String> filteredLinks,
                                    boolean photoOnly, boolean videoOnly) {
        long photoCount = allLinks.stream()
                .filter(link -> PHOTO_EXTENSIONS.stream()
                        .anyMatch(ext -> link.toLowerCase().endsWith(ext)))
                .count();

        long videoCount = allLinks.stream()
                .filter(link -> VIDEO_EXTENSIONS.stream()
                        .anyMatch(ext -> link.toLowerCase().endsWith(ext)))
                .count();

        long otherCount = allLinks.size() - photoCount - videoCount;

        StringBuilder summary = new StringBuilder("📊 **Ringkasan Media**\n\n");
        summary.append(String.format("📸 Foto: %d\n", photoCount));
        summary.append(String.format("🎬 Video: %d\n", videoCount));
        if (otherCount > 0) {
            summary.append(String.format("📄 Lainnya: %d\n", otherCount));
        }
        summary.append(String.format("📦 Total: %d\n", allLinks.size()));

        if (photoOnly && !videoOnly) {
            summary.append("\n🔍 Filter: Hanya foto");
        } else if (videoOnly && !photoOnly) {
            summary.append("\n🔍 Filter: Hanya video");
        } else if (photoOnly && videoOnly) {
            summary.append("\n🔍 Filter: Semua media");
        }

        summary.append(String.format("\n\n✅ Mengirim %d link...\n", filteredLinks.size()));

        return summary.toString();
    }

    private void sendText(long chatId, String text) {
        parseTextEntitiesUtils.formatText(text, new TdApi.TextParseModeMarkdown())
                        .thenAccept(parse -> sendMessageUtils.sendMessage(chatId, parse));
    }

    private void sendFile(long chatId, String content, String fileName) {
        try {
            File tempFile = File.createTempFile("coomer_", ".txt");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(content);
            }
            TdApi.InputFileLocal inputFile = new TdApi.InputFileLocal(tempFile.getAbsolutePath());
            TdApi.InputMessageDocument documentContent = new TdApi.InputMessageDocument(
                    inputFile,
                    null,
                    false,
                    new TdApi.FormattedText("📝 " + fileName, new TdApi.TextEntity[0])
            );
            sendMessageUtils.sendMessage(chatId, documentContent);
            tempFile.deleteOnExit();

        } catch (IOException e) {
            log.error("Gagal membuat file temporary", e);
            sendText(chatId, "❌ Gagal membuat file: " + e.getMessage());
        }
    }
}