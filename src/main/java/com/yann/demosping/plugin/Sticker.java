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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Sticker {

    private static final int MAX_QUOTE_COUNT = 20;
    private static final int DEFAULT_QUOTE_COUNT = 1;
    private static final String QUOTE_BACKGROUND_COLOR = "#1b1429";
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

    @UserBotCommand(commands = {"q", "quote"}, description = "Convert message to sticker", sudoOnly = true)
    public void sticker(TdApi.UpdateNewMessage message, String args) {
        log.info("Processing quote command");

        long chatId = message.message.chatId;
        long commandMsgId = message.message.id;

        Optional<Long> replyMessageId = extractReplyMessageId(message);
        if (replyMessageId.isEmpty()) {
            sendMessageUtils.sendMessage(chatId, "❌ Reply to a message to quote it.");
            return;
        }

        int messageCount = parseMessageCount(args);
        log.info("Generating quote for {} message(s)", messageCount);

        processQuoteGeneration(chatId, commandMsgId, replyMessageId.get(), messageCount);
    }

    private Optional<Long> extractReplyMessageId(TdApi.UpdateNewMessage message) {
        if (message.message.replyTo instanceof TdApi.MessageReplyToMessage reply) {
            return Optional.of(reply.messageId);
        }
        return Optional.empty();
    }

    private int parseMessageCount(String args) {
        String[] tokens = args.trim().split("\\s+");
        if (tokens.length > 0 && tokens[0].matches("\\d+")) {
            int count = Integer.parseInt(tokens[0]);
            return Math.min(count, MAX_QUOTE_COUNT);
        }
        return DEFAULT_QUOTE_COUNT;
    }

    private void processQuoteGeneration(long chatId, long commandMsgId, long replyMessageId, int messageCount) {
        String statusText = String.format("🎨 <b>Processing %d messages...</b>", messageCount);

        sendMessageUtils.sendMessage(chatId, commandMsgId, statusText)
                .thenAccept(statusMsg -> {
                    int offset = -(messageCount - 1);
                    fetchAndProcessMessages(chatId, replyMessageId, statusMsg.id, messageCount, offset);
                })
                .exceptionally(ex -> {
                    log.error("Failed to send status message", ex);
                    return null;
                });
    }

    private void fetchAndProcessMessages(long chatId, long replyMessageId, long statusMsgId,
                                         int messageCount, int offset) {
        chatHistory.getMessages(chatId, messageCount, offset, replyMessageId)
                .thenAccept(history -> {
                    if (history.totalCount == 0) {
                        sendMessageUtils.sendMessage(chatId, "❌ No messages found.");
                        deleteStatusMessage(chatId, statusMsgId);
                        return;
                    }

                    List<TdApi.Message> sortedMessages = sortMessagesByDate(history.messages);
                    log.info("Processing {} messages", sortedMessages.size());

                    convertMessagesToQuotly(chatId, replyMessageId, statusMsgId, sortedMessages);
                })
                .exceptionally(ex -> {
                    log.error("Failed to fetch messages", ex);
                    sendMessageUtils.sendMessage(chatId, "❌ Failed to fetch messages.");
                    deleteStatusMessage(chatId, statusMsgId);
                    return null;
                });
    }

    private List<TdApi.Message> sortMessagesByDate(TdApi.Message[] messages) {
        List<TdApi.Message> messageList = new ArrayList<>(Arrays.asList(messages));
        messageList.sort(Comparator.comparingLong(m -> m.id));
        return messageList;
    }

    private void convertMessagesToQuotly(long chatId, long replyMessageId, long statusMsgId,
                                         List<TdApi.Message> messages) {
        List<CompletableFuture<QuotlyRequest.QuotlyMessage>> futures = messages.stream()
                .map(this::convertToQuotlyMessage)
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    List<QuotlyRequest.QuotlyMessage> quotlyMessages = futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList());

                    generateAndSendSticker(chatId, replyMessageId, statusMsgId, quotlyMessages);
                })
                .exceptionally(ex -> {
                    log.error("Failed to process messages", ex);
                    sendMessageUtils.sendMessage(chatId, "❌ Error processing messages: " + ex.getMessage());
                    deleteStatusMessage(chatId, statusMsgId);
                    return null;
                });
    }

    private CompletableFuture<QuotlyRequest.QuotlyMessage> convertToQuotlyMessage(TdApi.Message message) {
        CompletableFuture<QuotlyRequest.QuotlyMessage> future = new CompletableFuture<>();

        long senderId = extractSenderId(message);
        String messageText = extractMessageText(message.content);

        getUser.getUser(senderId).thenAccept(user -> {
            String userName = buildUserName(user);
            TdApi.File photoFile = extractUserPhoto(user);

            if (photoFile != null) {
                processWithPhoto(future, senderId, userName, messageText, photoFile);
            } else {
                future.complete(createQuotlyMessage(senderId, userName, messageText, null));
            }
        }).exceptionally(ex -> {
            log.warn("Failed to get user info for sender {}, using defaults", senderId, ex);
            future.complete(createQuotlyMessage(senderId, "Unknown User", messageText, null));
            return null;
        });

        return future;
    }

    private long extractSenderId(TdApi.Message message) {
        if (message.senderId instanceof TdApi.MessageSenderUser user) {
            return user.userId;
        }
        return 0L;
    }

    private String buildUserName(TdApi.User user) {
        StringBuilder name = new StringBuilder(user.firstName);
        if (!user.lastName.isEmpty()) {
            name.append(" ").append(user.lastName);
        }
        return name.toString();
    }

    private TdApi.File extractUserPhoto(TdApi.User user) {
        return (user.profilePhoto != null) ? user.profilePhoto.small : null;
    }

    private void processWithPhoto(CompletableFuture<QuotlyRequest.QuotlyMessage> future,
                                  long senderId, String userName, String messageText,
                                  TdApi.File photoFile) {
        downloadAndUploadPhoto(photoFile)
                .thenAccept(photoUrl -> {
                    if (photoUrl.isPresent()) {
                        QuotlyRequest.QuotlyPhoto photo = QuotlyRequest.QuotlyPhoto.builder()
                                .url(photoUrl.get())
                                .build();
                        future.complete(createQuotlyMessage(senderId, userName, messageText, photo));
                    } else {
                        future.complete(createQuotlyMessage(senderId, userName, messageText, null));
                    }
                })
                .exceptionally(ex -> {
                    log.warn("Failed to process photo, creating message without photo", ex);
                    future.complete(createQuotlyMessage(senderId, userName, messageText, null));
                    return null;
                });
    }

    private CompletableFuture<Optional<String>> downloadAndUploadPhoto(TdApi.File photoFile) {
        CompletableFuture<Optional<String>> future = new CompletableFuture<>();

        client.send(new TdApi.DownloadFile(photoFile.id, PHOTO_DOWNLOAD_PRIORITY, 0, 0, true),
                response -> {
                    if (response.isError()) {
                        future.complete(Optional.empty());
                        return;
                    }

                    try {
                        String photoPath = response.get().local.path;
                        Optional<String> base64 = encodeFileToBase64(photoPath);

                        if (base64.isPresent()) {
                            uploadPhotoToImgBB(base64.get(), future);
                        } else {
                            future.complete(Optional.empty());
                        }
                    } catch (Exception ex) {
                        log.error("Error processing downloaded photo", ex);
                        future.complete(Optional.empty());
                    }
                });

        return future;
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

    private void uploadPhotoToImgBB(String base64, CompletableFuture<Optional<String>> future) {
        imgBBService.uploadImage(base64)
                .subscribe(
                        imgUrl -> future.complete(Optional.of(imgUrl)),
                        error -> {
                            log.error("Failed to upload image to ImgBB", error);
                            future.complete(Optional.empty());
                        }
                );
    }

    private QuotlyRequest.QuotlyMessage createQuotlyMessage(long senderId, String userName,
                                                            String text, QuotlyRequest.QuotlyPhoto photo) {
        QuotlyRequest.QuotlySender.QuotlySenderBuilder senderBuilder = QuotlyRequest.QuotlySender.builder()
                .id(senderId)
                .name(userName);

        if (photo != null) {
            senderBuilder.photo(photo);
        }

        return QuotlyRequest.QuotlyMessage.builder()
                .text(text)
                .avatar(true)
                .from(senderBuilder.build())
                .build();
    }

    private void generateAndSendSticker(long chatId, long replyMessageId, long statusMsgId,
                                        List<QuotlyRequest.QuotlyMessage> messages) {
        QuotlyRequest request = QuotlyRequest.builder()
                .type("quote")
                .format("webp")
                .backgroundColor(QUOTE_BACKGROUND_COLOR)
                .messages(messages)
                .build();

        log.info("Sending request to Quotly API with {} messages", messages.size());

        quotlyService.generateStickerAsync(request)
                .subscribe(
                        stickerBytes -> sendStickerToChat(chatId, replyMessageId, statusMsgId, stickerBytes),
                        error -> handleStickerGenerationError(chatId, statusMsgId, error)
                );
    }

    private void sendStickerToChat(long chatId, long replyMessageId, long statusMsgId, byte[] stickerBytes) {
        try {
            File tempFile = createTempStickerFile(stickerBytes);

            TdApi.InputMessageSticker sticker = new TdApi.InputMessageSticker();
            sticker.sticker = new TdApi.InputFileLocal(tempFile.getAbsolutePath());

            TdApi.InputMessageReplyToMessage replyTo = new TdApi.InputMessageReplyToMessage(replyMessageId, null, 0);

            client.send(new TdApi.SendMessage(chatId, 0, replyTo, null, null, sticker), sent -> {
                deleteStatusMessage(chatId, statusMsgId);
                deleteTempFile(tempFile);
            });

            log.info("Sticker sent successfully to chat {}", chatId);
        } catch (IOException ex) {
            log.error("Failed to create or send sticker file", ex);
            sendMessageUtils.sendMessage(chatId, "❌ Failed to save sticker file.");
            deleteStatusMessage(chatId, statusMsgId);
        }
    }

    private File createTempStickerFile(byte[] stickerBytes) throws IOException {
        File tempFile = File.createTempFile("sticker_", ".webp");
        Files.write(tempFile.toPath(), stickerBytes);
        return tempFile;
    }

    private void handleStickerGenerationError(long chatId, long statusMsgId, Throwable error) {
        log.error("Failed to generate sticker", error);
        sendMessageUtils.sendMessage(chatId, "❌ Failed to generate sticker: " + error.getMessage());
        deleteStatusMessage(chatId, statusMsgId);
    }

    private void deleteStatusMessage(long chatId, long messageId) {
        client.send(new TdApi.DeleteMessages(chatId, new long[]{messageId}, true));
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                log.warn("Failed to delete temp file: {}", file.getAbsolutePath());
            }
        }
    }

    private String extractMessageText(TdApi.MessageContent content) {
        return switch (content) {
            case TdApi.MessageText text -> text.text.text;
            case TdApi.MessagePhoto ignored -> "📷 Photo";
            case TdApi.MessageSticker ignored -> "🧩 Sticker";
            case TdApi.MessageVideo ignored -> "🎥 Video";
            case TdApi.MessageVoiceNote ignored -> "🎤 Voice Note";
            case TdApi.MessageAudio ignored -> "🎵 Audio";
            case TdApi.MessageDocument ignored -> "📄 Document";
            default -> "Unsupported Media";
        };
    }
}