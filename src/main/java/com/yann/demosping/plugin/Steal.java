package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.utils.CopyMessageUtils;
import com.yann.demosping.utils.MessageLinkResolver;
import it.tdlight.client.GenericResultHandler;
import it.tdlight.client.Result;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class Steal {

    private static final String LINK_ARG = "l";
    private static final int HIGHEST_PRIORITY = 32;

    private final SimpleTelegramClient client;
    private final MessageLinkResolver messageLinkResolver;
    private final CopyMessageUtils copyMessageUtils;

    @UserBotCommand(
            commands = {"steal"},
            description = "Steal protected content from link or replied message",
            sudoOnly = true
    )
    public void stealContent(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;

        Optional<String> link = extractLink(args);

        if (link.isEmpty()) {
            if (message.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo) {
                handleReplyMessage(chatId, messageId, replyTo.messageId);
                return;
            }
            updateStatus(chatId, messageId, "❌ Link not found and no reply detected. Use: <code>/steal -l https://t.me/c/..</code> or reply to a message");
            return;
        }

        updateStatus(chatId, messageId, "🔍 Resolving link...");
        processMessageLink(chatId, messageId, link.get());
    }

    private Optional<String> extractLink(String args) {
        Map<String, String> parsedArgs = ArgsParser.parse(args);
        String link = parsedArgs.getOrDefault(LINK_ARG, "");
        return link.isEmpty() || "error".equals(link) ? Optional.empty() : Optional.of(link);
    }

    private void processMessageLink(long chatId, long messageId, String link) {
        messageLinkResolver.resolve(link).thenAcceptAsync(messageSource -> {
            if (messageSource == null) {
                updateStatus(chatId, messageId, "❌ Message not found or invalid link.");
                return;
            }

            getChatInfo(messageSource.chatId)
                    .thenAccept(chat -> handleMessageContent(chatId, messageId, messageSource, chat))
                    .exceptionally(e -> handleError(chatId, messageId, "Failed to get chat info", e));
        });
    }

    private void handleReplyMessage(long chatId, long commandMessageId, long repliedMessageId) {
        updateStatus(chatId, commandMessageId, "🔍 Processing replied message...");

        client.send(new TdApi.GetMessage(chatId, repliedMessageId), this.handleResult(result -> {
            TdApi.Message repliedMessage = result.get();

            getChatInfo(chatId)
                    .thenAccept(chat -> {
                        if (chat.hasProtectedContent) {
                            handleProtectedContentToSavedMessages(commandMessageId, repliedMessage);
                        } else {
                            forwardToSavedMessages(commandMessageId, repliedMessage);
                        }
                    })
                    .exceptionally(e -> handleError(chatId, commandMessageId, "Failed to process replied message", e));
        }, error -> updateStatus(chatId, commandMessageId, "❌ Failed to get replied message: " + error.message)));
    }

    private void forwardToSavedMessages(long commandMessageId, TdApi.Message message) {
        getSavedMessagesChatId().thenAccept(savedChatId -> {
            try {
                TdApi.InputMessageContent inputContent = copyMessageUtils.convertToInput(message.content);
                client.send(new TdApi.SendMessage(savedChatId, 0, null, null, null, inputContent),
                        this.handleResult(
                                result -> {
                                    updateStatusInOriginalChat(message.chatId, commandMessageId, "✅ Saved to Saved Messages");
                                    deleteMessageDelayed(message.chatId, commandMessageId);
                                },
                                error -> updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Failed to save: " + error.message)
                        ));
            } catch (Exception e) {
                updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Exception: " + e.getMessage());
            }
        }).exceptionally(e -> {
            updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Failed to get Saved Messages: " + e.getMessage());
            return null;
        });
    }

    private void handleProtectedContentToSavedMessages(long commandMessageId, TdApi.Message message) {
        if (message.content instanceof TdApi.MessageText text) {
            sendTextToSavedMessages(commandMessageId, message.chatId, text);
            return;
        }

        int fileId = extractFileId(message.content);
        if (fileId == 0) {
            updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Media type not supported for stealing.");
            return;
        }

        updateStatusInOriginalChat(message.chatId, commandMessageId, "🔓 Protected content detected.\n⬇️ Downloading...");
        downloadAndUploadToSavedMessages(commandMessageId, message, fileId);
    }

    private void sendTextToSavedMessages(long commandMessageId, long originalChatId, TdApi.MessageText text) {
        getSavedMessagesChatId().thenAccept(savedChatId -> {
            TdApi.InputMessageText inputText = new TdApi.InputMessageText(text.text, null, true);
            client.send(new TdApi.SendMessage(savedChatId, 0, null, null, null, inputText),
                    this.handleResult(
                            result -> {
                                updateStatusInOriginalChat(originalChatId, commandMessageId, "✅ Saved to Saved Messages");
                                deleteMessageDelayed(originalChatId, commandMessageId);
                            },
                            error -> updateStatusInOriginalChat(originalChatId, commandMessageId, "❌ Failed to save: " + error.message)
                    ));
        });
    }

    // --- UPDATED METHOD: Uses DeleteFile logic ---
    private void downloadAndUploadToSavedMessages(long commandMessageId, TdApi.Message message, int fileId) {

        client.send(new TdApi.DownloadFile(fileId, HIGHEST_PRIORITY, 0, 0, true),
                this.handleResult(downloadResult -> {
                    TdApi.File file = downloadResult.get();
                    String localPath = file.local.path;
                    log.info("✓ File downloaded to: {}", localPath);

                    updateStatusInOriginalChat(message.chatId, commandMessageId, "⬆️ Uploading to Saved Messages...");

                    getSavedMessagesChatId().thenAccept(savedChatId -> {
                        String safePath = copyToTemp(localPath);

                        client.send(new TdApi.DeleteFile(fileId), this.handleResult(
                                deleteOk -> uploadSafeFile(savedChatId, message, commandMessageId, safePath, localPath),
                                deleteError -> uploadSafeFile(savedChatId, message, commandMessageId, safePath, localPath)
                        ));
                    }).exceptionally(e -> {
                        cleanupFile(localPath);
                        return null;
                    });
                }, error -> updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Download failed: " + error.message)));
    }

    private void uploadSafeFile(long targetChatId, TdApi.Message originalMessage, long commandMessageId, String safePath, String originalPath) {
        TdApi.InputFile inputFile = new TdApi.InputFileLocal(safePath);
        TdApi.InputMessageContent content = createInputContent(originalMessage.content, inputFile);

        if (content == null) {
            updateStatusInOriginalChat(originalMessage.chatId, commandMessageId, "❌ Failed to create content.");
            cleanupFile(safePath);
            cleanupFile(originalPath);
            return;
        }

        client.send(new TdApi.SendMessage(targetChatId, 0, null, null, null, content),
                this.handleResult(
                        uploadResult -> {
                            updateStatusInOriginalChat(originalMessage.chatId, commandMessageId, "✅ Saved successfully");
                            deleteMessageDelayed(originalMessage.chatId, commandMessageId);
                            cleanupFile(safePath);
                            cleanupFile(originalPath);
                        },
                        error -> {
                            updateStatusInOriginalChat(originalMessage.chatId, commandMessageId, "❌ Upload failed: " + error.message);
                            cleanupFile(safePath);
                            cleanupFile(originalPath);
                        }
                ));
    }

    private CompletableFuture<Long> getSavedMessagesChatId() {
        CompletableFuture<Long> future = new CompletableFuture<>();
        client.send(new TdApi.GetMe(), this.handleResult(
                meResult -> client.send(new TdApi.CreatePrivateChat(meResult.get().id, true),
                        this.handleResult(
                                chatResult -> future.complete(chatResult.get().id),
                                error -> future.completeExceptionally(new RuntimeException(error.message))
                        )),
                error -> future.completeExceptionally(new RuntimeException(error.message))
        ));
        return future;
    }

    private void updateStatusInOriginalChat(long chatId, long messageId, String text) {
        updateStatus(chatId, messageId, text);
    }

    private void deleteMessageDelayed(long chatId, long messageId) {
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                deleteMessage(chatId, messageId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void handleMessageContent(long chatId, long messageId, TdApi.Message messageSource, TdApi.Chat chat) {
        if (chat.hasProtectedContent) {
            handleProtectedContent(chatId, messageId, messageSource);
        } else {
            forwardUnprotectedContent(chatId, messageId, messageSource);
        }
    }

    private void handleProtectedContent(long chatId, long messageId, TdApi.Message messageSource) {
        if (messageSource.content instanceof TdApi.MessageText text) {
            sendTextMessage(chatId, messageId, text);
            return;
        }

        int fileId = extractFileId(messageSource.content);
        if (fileId == 0) {
            updateStatus(chatId, messageId, "❌ Media type not supported.");
            return;
        }

        updateStatus(chatId, messageId, "🔓 Protected content detected.\n⬇️ Downloading...");
        downloadAndReupload(chatId, messageId, messageSource, fileId);
    }

    private void sendTextMessage(long chatId, long messageId, TdApi.MessageText text) {
        TdApi.InputMessageText inputText = new TdApi.InputMessageText(text.text, null, true);
        client.send(new TdApi.SendMessage(chatId, 0, null, null, null, inputText));
        deleteMessage(chatId, messageId);
    }

    private void forwardUnprotectedContent(long chatId, long messageId, TdApi.Message messageSource) {
        TdApi.InputMessageContent inputContent = copyMessageUtils.convertToInput(messageSource.content);
        client.send(new TdApi.SendMessage(chatId, 0, null, null, null, inputContent));
        deleteMessage(chatId, messageId);
    }

    private void downloadAndReupload(long chatId, long messageId, TdApi.Message messageSource, int fileId) {
        client.send(new TdApi.DownloadFile(fileId, HIGHEST_PRIORITY, 0, 0, true),
                this.handleResult(result -> {
                    String localPath = result.get().local.path;


                    updateStatus(chatId, messageId, "⬆️ Uploading...");
                    reuploadMedia(chatId, messageId, messageSource, localPath, fileId);
                }, error -> updateStatus(chatId, messageId, "❌ Download failed: " + error.message)));
    }

    private int extractFileId(TdApi.MessageContent content) {
        return switch (content) {
            case TdApi.MessagePhoto p -> p.photo.sizes[p.photo.sizes.length - 1].photo.id;
            case TdApi.MessageVideo v -> v.video.video.id;
            case TdApi.MessageDocument d -> d.document.document.id;
            case TdApi.MessageAudio a -> a.audio.audio.id;
            case TdApi.MessageVoiceNote vn -> vn.voiceNote.voice.id;
            case TdApi.MessageAnimation anim -> anim.animation.animation.id;
            case TdApi.MessageSticker s -> s.sticker.sticker.id;
            default -> 0;
        };
    }

    private void reuploadMedia(long chatId, long messageId, TdApi.Message originalMessage, String localPath, int originalFileId) {

        String safePath = copyToTemp(localPath);
        client.send(new TdApi.DeleteFile(originalFileId), this.handleResult(
                deleteOk -> sendFinalFile(chatId, messageId, originalMessage, safePath, localPath),
                deleteError -> sendFinalFile(chatId, messageId, originalMessage, safePath, localPath)
        ));
    }

    private void sendFinalFile(long chatId, long messageId, TdApi.Message originalMessage, String safePath, String originalPath) {
        TdApi.InputFile inputFile = new TdApi.InputFileLocal(safePath);
        TdApi.InputMessageContent content = createInputContent(originalMessage.content, inputFile);

        if (content == null) {
            updateStatus(chatId, messageId, "❌ Failed to create input content.");
            cleanupFile(safePath);
            cleanupFile(originalPath);
            return;
        }

        client.send(new TdApi.SendMessage(chatId, 0, null, null, null, content),
                this.handleResult(
                        result -> {
                            deleteMessage(chatId, messageId);
                            cleanupFile(safePath);
                            cleanupFile(originalPath);
                        },
                        error -> {
                            updateStatus(chatId, messageId, "❌ Failed to upload: " + error.message);
                            cleanupFile(safePath);
                            cleanupFile(originalPath);
                        }
                ));
    }


    private String copyToTemp(String originalPath) {
        try {
            File original = new File(originalPath);
            String tempDir = System.getProperty("java.io.tmpdir");
            String newName = UUID.randomUUID() + "_" + original.getName();
            File dest = new File(tempDir, newName);

            Files.copy(original.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dest.getAbsolutePath();
        } catch (Exception e) {
            return originalPath;
        }
    }

    private TdApi.InputMessageContent createInputContent(TdApi.MessageContent msgContent, TdApi.InputFile inputFile) {
        TdApi.FormattedText caption = extractCaption(msgContent);

        return switch (msgContent) {
            case TdApi.MessagePhoto ignored ->
                    new TdApi.InputMessagePhoto(inputFile, null, null, 0, 0, caption, false, null, false);

            case TdApi.MessageVideo v ->
                    new TdApi.InputMessageVideo(inputFile, null, null, 0, null, v.video.duration,
                            v.video.width, v.video.height, v.video.supportsStreaming, caption, false, null, false);

            case TdApi.MessageDocument ignored ->
                    new TdApi.InputMessageDocument(inputFile, null, false, caption);

            case TdApi.MessageAudio a ->
                    new TdApi.InputMessageAudio(inputFile, null, a.audio.duration,
                            a.audio.title, a.audio.performer, caption);

            case TdApi.MessageVoiceNote vn ->
                    new TdApi.InputMessageVoiceNote(inputFile, vn.voiceNote.duration,
                            vn.voiceNote.waveform, caption, null);

            case TdApi.MessageAnimation anim ->
                    new TdApi.InputMessageAnimation(inputFile, null, null, anim.animation.duration,
                            anim.animation.width, anim.animation.height, caption, false, false);

            case TdApi.MessageSticker ignored ->
                    new TdApi.InputMessageSticker(inputFile, null, 0, 0, null);

            default -> null;
        };
    }

    private TdApi.FormattedText extractCaption(TdApi.MessageContent content) {
        return switch (content) {
            case TdApi.MessagePhoto p -> p.caption;
            case TdApi.MessageVideo v -> v.caption;
            case TdApi.MessageDocument d -> d.caption;
            case TdApi.MessageAudio a -> a.caption;
            case TdApi.MessageAnimation anim -> anim.caption;
            case TdApi.MessageVoiceNote vn -> vn.caption;
            default -> null;
        };
    }

    private void updateStatus(long chatId, long messageId, String text) {
        TdApi.InputMessageText inputText = new TdApi.InputMessageText(
                new TdApi.FormattedText(text, null), null, true
        );
        client.send(new TdApi.EditMessageText(chatId, messageId, null, inputText));
    }

    private void deleteMessage(long chatId, long messageId) {
        client.send(new TdApi.DeleteMessages(chatId, new long[]{messageId}, true));
    }

    private void cleanupFile(String localPath) {
        try {
            new File(localPath).delete();
        } catch (Exception e) {
            log.warn("Failed to delete temporary file: {}", localPath, e);
        }
    }

    private CompletableFuture<TdApi.Chat> getChatInfo(Long chatId) {
        CompletableFuture<TdApi.Chat> future = new CompletableFuture<>();
        client.send(new TdApi.GetChat(chatId),
                this.handleResult(
                        result -> future.complete(result.get()),
                        error -> future.completeExceptionally(new RuntimeException("Chat Not Found"))
                ));
        return future;
    }

    private Void handleError(long chatId, long messageId, String message, Throwable e) {
        log.error(message, e);
        updateStatus(chatId, messageId, "❌ " + message + ": " + e.getMessage());
        return null;
    }

    private <T extends TdApi.Object> GenericResultHandler<T> handleResult(
            java.util.function.Consumer<Result<T>> onSuccess,
            java.util.function.Consumer<TdApi.Error> onError) {
        return result -> {
            if (result.isError()) {
                onError.accept(result.getError());
            } else {
                onSuccess.accept(result);
            }
        };
    }
}