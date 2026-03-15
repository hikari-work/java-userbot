package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.dto.QuotlyRequest;
import com.yann.demosping.service.ImgBBService;
import com.yann.demosping.service.QuotlyRequestService;
import com.yann.demosping.service.ChatHistory;
import com.yann.demosping.service.GetUser;
import com.yann.demosping.service.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Slf4j
@Component
public class Sticker {

    private static final int MAX_QUOTE_COUNT = 20;
    private static final int DEFAULT_QUOTE_COUNT = 1;
    private static final String DEFAULT_BG_COLOR = "#1b1429";
    private static final int PHOTO_DOWNLOAD_PRIORITY = 32;

    private final SimpleTelegramClient client;
    private final SendMessageUtils sendMessageUtils;
    private final ChatHistory chatHistory;
    private final GetUser getUser;
    private final QuotlyRequestService quotlyService;
    private final ImgBBService imgBBService;

    public Sticker(@Qualifier("userBotClient") SimpleTelegramClient client,
                   SendMessageUtils sendMessageUtils,
                   ChatHistory chatHistory,
                   GetUser getUser,
                   QuotlyRequestService quotlyService,
                   ImgBBService imgBBService) {
        this.client = client;
        this.sendMessageUtils = sendMessageUtils;
        this.chatHistory = chatHistory;
        this.getUser = getUser;
        this.quotlyService = quotlyService;
        this.imgBBService = imgBBService;
    }

    /**
     * Usage: ,q [count] [--bg COLOR] [--scale N] [--format webp|png] [--emoji BRAND]
     *
     * Examples:
     *   ,q                        → 1 message, default style
     *   ,q 5                      → last 5 messages
     *   ,q --bg random            → random background
     *   ,q 3 --bg #ff6600 --scale 3 --emoji google
     */
    @UserBotCommand(commands = {"q", "quote"}, description = "Convert message to sticker. Args: [count] [--bg COLOR] [--scale N] [--format webp|png] [--emoji BRAND]", sudoOnly = true)
    public void sticker(TdApi.UpdateNewMessage message, String args) {
        log.info("Processing quote command");

        long chatId = message.message.chatId;
        long commandMsgId = message.message.id;

        Optional<Long> replyMessageId = extractReplyMessageId(message);
        if (replyMessageId.isEmpty()) {
            sendMessageUtils.sendMessage(chatId, "❌ Reply to a message to quote it.").subscribe();
            return;
        }

        QuoteOptions options = parseArgs(args);
        log.info("Generating quote: count={} bg={} scale={} format={} emoji={}",
                options.count(), options.bgColor(), options.scale(), options.format(), options.emojiBrand());

        processQuoteGeneration(chatId, commandMsgId, replyMessageId.get(), options);
    }

    // ── Arg parsing ─────────────────────────────────────────────────────────

    private record QuoteOptions(int count, String bgColor, Float scale, String format, String emojiBrand) {}

    private QuoteOptions parseArgs(String args) {
        String[] tokens = args.trim().split("\\s+");
        int count = DEFAULT_QUOTE_COUNT;
        String bgColor = DEFAULT_BG_COLOR;
        Float scale = null;
        String format = "webp";
        String emojiBrand = null;

        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            switch (t) {
                case "--bg", "-bg" -> { if (i + 1 < tokens.length) bgColor = tokens[++i]; }
                case "--scale", "-scale" -> {
                    if (i + 1 < tokens.length) {
                        try { scale = Float.parseFloat(tokens[++i]); } catch (NumberFormatException ignored) {}
                    }
                }
                case "--format", "-format" -> { if (i + 1 < tokens.length) format = tokens[++i]; }
                case "--emoji", "-emoji" -> { if (i + 1 < tokens.length) emojiBrand = tokens[++i]; }
                default -> {
                    if (t.matches("\\d+")) {
                        count = Math.min(Integer.parseInt(t), MAX_QUOTE_COUNT);
                    }
                }
            }
        }
        return new QuoteOptions(count, bgColor, scale, format, emojiBrand);
    }

    // ── Flow ─────────────────────────────────────────────────────────────────

    private Optional<Long> extractReplyMessageId(TdApi.UpdateNewMessage message) {
        if (message.message.replyTo instanceof TdApi.MessageReplyToMessage reply) {
            return Optional.of(reply.messageId);
        }
        return Optional.empty();
    }

    private void processQuoteGeneration(long chatId, long commandMsgId, long replyMessageId, QuoteOptions options) {
        String statusText = String.format("🎨 <b>Processing %d message(s)...</b>", options.count());

        sendMessageUtils.sendMessage(chatId, commandMsgId, statusText)
                .subscribe(statusMsg -> {
                    int offset = -(options.count() - 1);
                    fetchAndProcessMessages(chatId, commandMsgId, replyMessageId, statusMsg.id, options, offset);
                }, ex -> log.error("Failed to send status message", ex));
    }

    private void fetchAndProcessMessages(long chatId, long commandMsgId, long replyMessageId, long statusMsgId,
                                         QuoteOptions options, int offset) {
        chatHistory.getMessages(chatId, options.count(), offset, replyMessageId)
                .subscribe(history -> {
                    if (history.totalCount == 0) {
                        sendMessageUtils.sendMessage(chatId, "❌ No messages found.").subscribe();
                        deleteMessages(chatId, statusMsgId, commandMsgId);
                        return;
                    }
                    List<TdApi.Message> sorted = sortMessagesByDate(history.messages);
                    log.info("Processing {} messages", sorted.size());
                    convertMessagesToQuotly(chatId, commandMsgId, replyMessageId, statusMsgId, sorted, options);
                }, ex -> {
                    log.error("Failed to fetch messages", ex);
                    sendMessageUtils.sendMessage(chatId, "❌ Failed to fetch messages: " + ex.getMessage()).subscribe();
                    deleteMessages(chatId, statusMsgId, commandMsgId);
                });
    }

    private List<TdApi.Message> sortMessagesByDate(TdApi.Message[] messages) {
        List<TdApi.Message> list = new ArrayList<>(Arrays.asList(messages));
        list.sort(Comparator.comparingLong(m -> m.id));
        return list;
    }

    private void convertMessagesToQuotly(long chatId, long commandMsgId, long replyMessageId, long statusMsgId,
                                         List<TdApi.Message> messages, QuoteOptions options) {
        Flux.fromIterable(messages)
                .flatMap(this::convertToQuotlyMessage)
                .collectList()
                .subscribe(
                        quotlyMessages -> generateAndSendSticker(chatId, commandMsgId, replyMessageId, statusMsgId, quotlyMessages, options),
                        ex -> {
                            log.error("Failed to process messages", ex);
                            sendMessageUtils.sendMessage(chatId, "❌ Error processing messages: " + ex.getMessage()).subscribe();
                            deleteMessages(chatId, statusMsgId, commandMsgId);
                        }
                );
    }

    // ── Message → QuotlyMessage conversion ──────────────────────────────────

    private Mono<QuotlyRequest.QuotlyMessage> convertToQuotlyMessage(TdApi.Message message) {
        long senderId = extractSenderId(message);
        TextContent textContent = extractTextContent(message.content);

        return getUser.getUser(senderId)
                .flatMap(user -> {
                    String userName = buildUserName(user);
                    TdApi.File photoFile = extractUserPhoto(user);

                    QuotlyRequest.QuotlyMessage.QuotlyMessageBuilder msgBuilder = QuotlyRequest.QuotlyMessage.builder()
                            .text(textContent.text())
                            .entities(textContent.entities())
                            .avatar(true);

                    applyMediaContent(message.content, msgBuilder);
                    applyReplyContext(message, msgBuilder);

                    if (photoFile != null) {
                        return processWithPhoto(senderId, userName, msgBuilder, photoFile);
                    } else {
                        return Mono.just(msgBuilder.from(buildSender(senderId, userName, null)).build());
                    }
                })
                .onErrorReturn(buildFallbackMessage(senderId, textContent));
    }

    private long extractSenderId(TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser user) {
            return user.userId;
        }
        return 0L;
    }

    private String buildUserName(TdApi.User user) {
        StringBuilder name = new StringBuilder(user.firstName);
        if (!user.lastName.isEmpty()) name.append(" ").append(user.lastName);
        return name.toString();
    }

    private TdApi.File extractUserPhoto(TdApi.User user) {
        return (user.profilePhoto != null) ? user.profilePhoto.small : null;
    }

    private QuotlyRequest.QuotlySender buildSender(long id, String name, QuotlyRequest.QuotlyPhoto photo) {
        return QuotlyRequest.QuotlySender.builder()
                .id(id)
                .name(name)
                .photo(photo)
                .build();
    }

    private QuotlyRequest.QuotlyMessage buildFallbackMessage(long senderId, TextContent content) {
        return QuotlyRequest.QuotlyMessage.builder()
                .text(content.text())
                .entities(content.entities())
                .avatar(true)
                .from(buildSender(senderId, "Unknown User", null))
                .build();
    }

    private void applyMediaContent(TdApi.MessageContent content,
                                   QuotlyRequest.QuotlyMessage.QuotlyMessageBuilder builder) {
        if (content instanceof TdApi.MessageSticker sticker) {
            builder.mediaType("sticker");
        }
    }

    private void applyReplyContext(TdApi.Message message,
                                   QuotlyRequest.QuotlyMessage.QuotlyMessageBuilder builder) {
        if (!(message.replyTo instanceof TdApi.MessageReplyToMessage replyTo)) return;
        if (replyTo.content instanceof TdApi.MessageText replyText) {
            builder.replyMessage(QuotlyRequest.QuotlyReplyMessage.builder()
                    .text(replyText.text.text)
                    .entities(mapEntities(replyText.text))
                    .build());
        }
    }

    private Mono<QuotlyRequest.QuotlyMessage> processWithPhoto(long senderId, String userName,
                                                               QuotlyRequest.QuotlyMessage.QuotlyMessageBuilder msgBuilder,
                                                               TdApi.File photoFile) {
        return downloadAndUploadPhoto(photoFile)
                .map(photoUrl -> {
                    QuotlyRequest.QuotlyPhoto photo = photoUrl.map(url ->
                            QuotlyRequest.QuotlyPhoto.builder().url(url).build()
                    ).orElse(null);
                    return msgBuilder.from(buildSender(senderId, userName, photo)).build();
                })
                .onErrorReturn(msgBuilder.from(buildSender(senderId, userName, null)).build());
    }

    private Mono<Optional<String>> downloadAndUploadPhoto(TdApi.File photoFile) {
        return Mono.<Optional<String>>create(sink ->
                client.send(new TdApi.DownloadFile(photoFile.id, PHOTO_DOWNLOAD_PRIORITY, 0, 0, true), response -> {
                    if (response.isError()) {
                        sink.success(Optional.empty());
                        return;
                    }
                    try {
                        sink.success(encodeFileToBase64(response.get().local.path));
                    } catch (Exception ex) {
                        log.error("Error processing downloaded photo", ex);
                        sink.success(Optional.empty());
                    }
                })
        ).flatMap(base64 -> {
            if (base64.isEmpty()) return Mono.just(Optional.<String>empty());
            return imgBBService.uploadImage(base64.get())
                    .map(Optional::of)
                    .onErrorReturn(Optional.empty());
        });
    }

    private Optional<String> encodeFileToBase64(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && file.length() > 0) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                return Optional.of(Base64.getEncoder().encodeToString(bytes));
            }
        } catch (IOException ex) {
            log.error("Failed to encode file to base64: {}", filePath, ex);
        }
        return Optional.empty();
    }

    // ── Entity mapping ───────────────────────────────────────────────────────

    private record TextContent(String text, List<QuotlyRequest.QuotlyEntity> entities) {}

    private TextContent extractTextContent(TdApi.MessageContent content) {
        return switch (content) {
            case TdApi.MessageText mt -> new TextContent(mt.text.text, mapEntities(mt.text));
            case TdApi.MessagePhoto ignored -> new TextContent("📷 Photo", List.of());
            case TdApi.MessageSticker ignored -> new TextContent("🧩 Sticker", List.of());
            case TdApi.MessageVideo ignored -> new TextContent("🎥 Video", List.of());
            case TdApi.MessageVoiceNote ignored -> new TextContent("🎤 Voice Note", List.of());
            case TdApi.MessageAudio ignored -> new TextContent("🎵 Audio", List.of());
            case TdApi.MessageDocument ignored -> new TextContent("📄 Document", List.of());
            default -> new TextContent("Unsupported Media", List.of());
        };
    }

    private List<QuotlyRequest.QuotlyEntity> mapEntities(TdApi.FormattedText formattedText) {
        if (formattedText.entities == null || formattedText.entities.length == 0) return List.of();

        List<QuotlyRequest.QuotlyEntity> result = new ArrayList<>();
        for (TdApi.TextEntity entity : formattedText.entities) {
            String type = mapEntityType(entity.type);
            if (type == null) continue;

            QuotlyRequest.QuotlyEntity.QuotlyEntityBuilder builder = QuotlyRequest.QuotlyEntity.builder()
                    .type(type)
                    .offset(entity.offset)
                    .length(entity.length);

            if (entity.type instanceof TdApi.TextEntityTypeTextUrl textUrl) builder.url(textUrl.url);
            if (entity.type instanceof TdApi.TextEntityTypePreCode preCode) builder.language(preCode.language);

            result.add(builder.build());
        }
        return result;
    }

    private String mapEntityType(TdApi.TextEntityType type) {
        return switch (type) {
            case TdApi.TextEntityTypeBold ignored -> "bold";
            case TdApi.TextEntityTypeItalic ignored -> "italic";
            case TdApi.TextEntityTypeUnderline ignored -> "underline";
            case TdApi.TextEntityTypeStrikethrough ignored -> "strikethrough";
            case TdApi.TextEntityTypeCode ignored -> "code";
            case TdApi.TextEntityTypePre ignored -> "pre";
            case TdApi.TextEntityTypePreCode ignored -> "pre";
            case TdApi.TextEntityTypeTextUrl ignored -> "text_link";
            default -> null;
        };
    }

    // ── Sticker generation and sending ───────────────────────────────────────

    private void generateAndSendSticker(long chatId, long commandMsgId, long replyMessageId, long statusMsgId,
                                        List<QuotlyRequest.QuotlyMessage> messages, QuoteOptions options) {
        QuotlyRequest.QuotlyRequestBuilder requestBuilder = QuotlyRequest.builder()
                .type("quote")
                .format(options.format())
                .backgroundColor(options.bgColor())
                .messages(messages);

        if (options.scale() != null) requestBuilder.scale(options.scale());
        if (options.emojiBrand() != null) requestBuilder.emojiBrand(options.emojiBrand());

        QuotlyRequest request = requestBuilder.build();
        log.info("Sending request to Quotly API with {} messages", messages.size());

        quotlyService.generateStickerAsync(request)
                .subscribe(
                        stickerBytes -> sendStickerToChat(chatId, commandMsgId, replyMessageId, statusMsgId, stickerBytes),
                        error -> handleStickerGenerationError(chatId, commandMsgId, statusMsgId, error)
                );
    }

    private void sendStickerToChat(long chatId, long commandMsgId, long replyMessageId,
                                   long statusMsgId, byte[] stickerBytes) {
        try {
            File tempFile = File.createTempFile("sticker_", ".webp");
            Files.write(tempFile.toPath(), stickerBytes);

            // PreliminaryUploadFile gives TDLib a proper upload context.
            // Using InputFileLocal directly causes "Can't resend local file" if TDLib needs to retry.
            TdApi.PreliminaryUploadFile upload = new TdApi.PreliminaryUploadFile(
                    new TdApi.InputFileLocal(tempFile.getAbsolutePath()),
                    new TdApi.FileTypeSticker(),
                    PHOTO_DOWNLOAD_PRIORITY
            );

            client.send(upload, uploadResult -> {
                if (uploadResult.isError()) {
                    log.error("Sticker upload failed: {}", uploadResult.getError().message);
                    sendMessageUtils.sendMessage(chatId, "❌ Upload failed: " + uploadResult.getError().message).subscribe();
                    deleteMessages(chatId, statusMsgId, commandMsgId);
                    deleteTempFile(tempFile);
                    return;
                }

                int fileId = uploadResult.get().id;
                TdApi.InputMessageSticker sticker = new TdApi.InputMessageSticker();
                sticker.sticker = new TdApi.InputFileId(fileId);

                TdApi.InputMessageReplyToMessage replyTo = new TdApi.InputMessageReplyToMessage(replyMessageId, null, 0);
                client.send(new TdApi.SendMessage(chatId, 0, replyTo, null, null, sticker), sent -> {
                    client.send(new TdApi.CancelPreliminaryUploadFile(fileId), r -> {});
                    deleteMessages(chatId, statusMsgId, commandMsgId);
                    if (sent.isError()) {
                        log.error("Failed to send sticker: {}", sent.getError().message);
                    } else {
                        log.info("Sticker sent to chat {}", chatId);
                    }
                    deleteTempFile(tempFile);
                });
            });

        } catch (IOException ex) {
            log.error("Failed to create sticker temp file", ex);
            sendMessageUtils.sendMessage(chatId, "❌ Failed to save sticker file.").subscribe();
            deleteMessages(chatId, statusMsgId, commandMsgId);
        }
    }

    private void handleStickerGenerationError(long chatId, long commandMsgId, long statusMsgId, Throwable error) {
        log.error("Failed to generate sticker", error);
        String msg = (error instanceof QuotlyRequestService.QuotlyException)
                ? "❌ " + error.getMessage()
                : "❌ Failed to generate sticker: " + error.getMessage();
        sendMessageUtils.sendMessage(chatId, msg).subscribe();
        deleteMessages(chatId, statusMsgId, commandMsgId);
    }

    private void deleteMessages(long chatId, long... messageIds) {
        client.send(new TdApi.DeleteMessages(chatId, messageIds, true));
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists() && !file.delete()) {
            log.warn("Failed to delete temp file: {}", file.getAbsolutePath());
        }
    }
}
