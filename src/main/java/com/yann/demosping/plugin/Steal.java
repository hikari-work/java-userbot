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
import java.io.FileOutputStream;
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
            // Check if message is a reply
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
        log.info("=== FORWARD TO SAVED MESSAGES START ===");
        log.info("Message ID: {}, Chat ID: {}, Content Type: {}", message.id, message.chatId, message.content.getClass().getSimpleName());

        getSavedMessagesChatId().thenAccept(savedChatId -> {
            log.info("✓ Got Saved Messages chat ID: {}", savedChatId);

            try {
                TdApi.InputMessageContent inputContent = copyMessageUtils.convertToInput(message.content);
                log.info("✓ Converted message content to InputMessageContent: {}", inputContent.getClass().getSimpleName());

                log.info("→ Sending message to Saved Messages chat {}...", savedChatId);
                client.send(new TdApi.SendMessage(savedChatId, 0, null, null, null, inputContent),
                        this.handleResult(
                                result -> {
                                    log.info("✓✓✓ SUCCESS! Message sent to Saved Messages. Message ID: {}", result.get().id);
                                    updateStatusInOriginalChat(message.chatId, commandMessageId, "✅ Saved to Saved Messages");
                                    deleteMessageDelayed(message.chatId, commandMessageId);
                                },
                                error -> {
                                    log.error("✗✗✗ FAILED to send to Saved Messages!");
                                    log.error("Error code: {}, Error message: {}", error.code, error.message);
                                    updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Failed to save: " + error.message);
                                }
                        ));
            } catch (Exception e) {
                log.error("✗✗✗ EXCEPTION while converting/sending message", e);
                updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Exception: " + e.getMessage());
            }
        }).exceptionally(e -> {
            log.error("✗✗✗ EXCEPTION in getSavedMessagesChatId", e);
            updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Failed to get Saved Messages: " + e.getMessage());
            return null;
        });
    }

    private void handleProtectedContentToSavedMessages(long commandMessageId, TdApi.Message message) {
        log.info("=== HANDLE PROTECTED CONTENT TO SAVED MESSAGES START ===");
        log.info("Message content type: {}", message.content.getClass().getSimpleName());

        if (message.content instanceof TdApi.MessageText text) {
            log.info("Content is TEXT, forwarding directly");
            sendTextToSavedMessages(commandMessageId, message.chatId, text);
            return;
        }

        int fileId = extractFileId(message.content);
        log.info("Extracted file ID: {}", fileId);

        if (fileId == 0) {
            log.error("File ID is 0 - media type not supported");
            updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Media type not supported for stealing.");
            return;
        }

        log.info("Starting download for file ID: {}", fileId);
        updateStatusInOriginalChat(message.chatId, commandMessageId, "🔓 Protected content detected.\n⬇️ Downloading...");
        downloadAndUploadToSavedMessages(commandMessageId, message, fileId);
    }

    private void sendTextToSavedMessages(long commandMessageId, long originalChatId, TdApi.MessageText text) {
        log.info("=== SEND TEXT TO SAVED MESSAGES START ===");
        log.info("Text length: {} characters", text.text.text.length());

        getSavedMessagesChatId().thenAccept(savedChatId -> {
            log.info("✓ Got Saved Messages chat ID: {}", savedChatId);

            TdApi.InputMessageText inputText = new TdApi.InputMessageText(text.text, null, true);
            log.info("✓ Created InputMessageText");

            log.info("→ Sending text message to Saved Messages...");
            client.send(new TdApi.SendMessage(savedChatId, 0, null, null, null, inputText),
                    this.handleResult(
                            result -> {
                                log.info("✓✓✓ SUCCESS! Text sent to Saved Messages. Message ID: {}", result.get().id);
                                updateStatusInOriginalChat(originalChatId, commandMessageId, "✅ Saved to Saved Messages");
                                deleteMessageDelayed(originalChatId, commandMessageId);
                            },
                            error -> {
                                log.error("✗✗✗ FAILED to send text to Saved Messages!");
                                log.error("Error code: {}, Error message: {}", error.code, error.message);
                                updateStatusInOriginalChat(originalChatId, commandMessageId, "❌ Failed to save: " + error.message);
                            }
                    ));
        }).exceptionally(e -> {
            log.error("✗✗✗ EXCEPTION in getSavedMessagesChatId for text", e);
            updateStatusInOriginalChat(originalChatId, commandMessageId, "❌ Failed to get Saved Messages: " + e.getMessage());
            return null;
        });
    }

    private void downloadAndUploadToSavedMessages(long commandMessageId, TdApi.Message message, int fileId) {
        log.info("=== DOWNLOAD AND UPLOAD TO SAVED MESSAGES START ===");
        log.info("File ID: {}, Message content type: {}", fileId, message.content.getClass().getSimpleName());

        client.send(new TdApi.DownloadFile(fileId, HIGHEST_PRIORITY, 0, 0, true),
                this.handleResult(downloadResult -> {
                    TdApi.File file = downloadResult.get();
                    String localPath = file.local.path;
                    log.info("✓ File downloaded successfully!");
                    log.info("  Local path: {}", localPath);
                    log.info("  File size: {} bytes", file.size);
                    log.info("  Downloaded: {} bytes", file.local.downloadedSize);
                    log.info("  File exists: {}", new File(localPath).exists());

                    updateStatusInOriginalChat(message.chatId, commandMessageId, "⬆️ Uploading to Saved Messages...");

                    log.info("→ Calling getSavedMessagesChatId()...");
                    getSavedMessagesChatId().thenAccept(savedChatId -> {
                        log.info("✓ INSIDE thenAccept callback - Got Saved Messages chat ID: {}", savedChatId);

                        // Modify file to force re-upload
                        String uploadPath = modifyFileForReupload(localPath);
                        log.info("✓ File modified for reupload: {}", uploadPath);

                        TdApi.InputFile inputFile = new TdApi.InputFileLocal(uploadPath);
                        log.info("✓ Created InputFileLocal with path: {}", uploadPath);

                        log.info("→ Calling createInputContent...");
                        TdApi.InputMessageContent content = createInputContent(message.content, inputFile);
                        log.info("← Returned from createInputContent");

                        if (content == null) {
                            log.error("✗✗✗ createInputContent returned NULL!");
                            log.error("Message content type was: {}", message.content.getClass().getSimpleName());
                            updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Failed to create input content (Type Unknown).");
                            cleanupFile(localPath);
                            if (!uploadPath.equals(localPath)) cleanupFile(uploadPath);
                            return;
                        }

                        log.info("✓ Content is NOT null, type: {}", content.getClass().getSimpleName());

                        // Create SendMessage request
                        log.info("→ Creating SendMessage request...");
                        TdApi.SendMessage sendRequest = new TdApi.SendMessage(
                                savedChatId,
                                0,
                                null,
                                null,
                                null,
                                content
                        );
                        log.info("✓ SendMessage request created");

                        log.info("→ About to call client.send() with SendMessage...");
                        log.info("  SendMessage details:");
                        log.info("    chatId: {}", sendRequest.chatId);
                        log.info("    content type: {}", sendRequest.inputMessageContent.getClass().getSimpleName());

                        try {
                            log.info("→→→ CALLING client.send() NOW...");
                            client.send(sendRequest,
                                    this.handleResult(
                                            uploadResult -> {
                                                log.info("✓✓✓ INSIDE SUCCESS CALLBACK!");
                                                TdApi.Message sentMessage = uploadResult.get();
                                                log.info("✓✓✓ SUCCESS! Media uploaded to Saved Messages!");
                                                log.info("  Uploaded message ID: {}", sentMessage.id);
                                                log.info("  Message chat ID: {}", sentMessage.chatId);
                                                log.info("  Sent message content type: {}", sentMessage.content.getClass().getSimpleName());
                                                updateStatusInOriginalChat(message.chatId, commandMessageId, "✅ Saved to Saved Messages");
                                                deleteMessageDelayed(message.chatId, commandMessageId);
                                                cleanupFile(localPath);
                                                if (!uploadPath.equals(localPath)) cleanupFile(uploadPath);
                                            },
                                            error -> {
                                                log.error("✗✗✗ INSIDE ERROR CALLBACK!");
                                                log.error("✗✗✗ FAILED to upload to Saved Messages!");
                                                log.error("Error code: {}, Error message: {}", error.code, error.message);
                                                log.error("Full error: {}", error);
                                                updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Failed to upload: " + error.message);
                                                cleanupFile(localPath);
                                                if (!uploadPath.equals(localPath)) cleanupFile(uploadPath);
                                            }
                                    ));
                            log.info("✓ client.send() call completed (callback registered)");
                        } catch (Exception e) {
                            log.error("✗✗✗ EXCEPTION during client.send() call!", e);
                            updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Exception during send: " + e.getMessage());
                            cleanupFile(localPath);
                            if (!uploadPath.equals(localPath)) cleanupFile(uploadPath);
                        }
                    }).exceptionally(e -> {
                        log.error("✗✗✗ EXCEPTION in getSavedMessagesChatId during upload", e);
                        updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Failed to get Saved Messages: " + e.getMessage());
                        cleanupFile(localPath);
                        return null;
                    });
                    log.info("✓ getSavedMessagesChatId().thenAccept() registered");
                }, error -> {
                    log.error("✗✗✗ FAILED to download file!");
                    log.error("Error code: {}, Error message: {}", error.code, error.message);
                    updateStatusInOriginalChat(message.chatId, commandMessageId, "❌ Download failed: " + error.message);
                }));
    }

    private CompletableFuture<Long> getSavedMessagesChatId() {
        log.info("=== GET SAVED MESSAGES CHAT ID START ===");
        log.info("Thread: {}", Thread.currentThread().getName());
        CompletableFuture<Long> future = new CompletableFuture<>();

        try {
            log.info("→ Calling client.send(GetMe)...");
            client.send(new TdApi.GetMe(), this.handleResult(
                    meResult -> {
                        log.info("✓ INSIDE GetMe SUCCESS callback");
                        long myUserId = meResult.get().id;
                        log.info("Current user ID from GetMe: {}", myUserId);

                        log.info("→ Calling client.send(CreatePrivateChat)...");
                        // Load the chat first before trying to send
                        client.send(new TdApi.CreatePrivateChat(myUserId, true), // force = true to ensure it's loaded
                                this.handleResult(
                                        chatResult -> {
                                            log.info("✓ INSIDE CreatePrivateChat SUCCESS callback");
                                            long chatId = chatResult.get().id;
                                            log.info("✓✓✓ Saved Messages chat ID obtained: {}", chatId);
                                            log.info("Chat type: {}", chatResult.get().type.getClass().getSimpleName());
                                            log.info("→ Completing future with chatId: {}", chatId);
                                            future.complete(chatId);
                                            log.info("✓ Future completed");
                                        },
                                        error -> {
                                            log.error("✗ INSIDE CreatePrivateChat ERROR callback");
                                            log.error("✗✗✗ Failed to create/get private chat!");
                                            log.error("Error code: {}, Error message: {}", error.code, error.message);
                                            future.completeExceptionally(new RuntimeException("Failed to get Saved Messages chat: " + error.message));
                                        }
                                ));
                        log.info("✓ CreatePrivateChat send() registered");
                    },
                    error -> {
                        log.error("✗ INSIDE GetMe ERROR callback");
                        log.error("✗✗✗ Failed to get current user!");
                        log.error("Error code: {}, Error message: {}", error.code, error.message);
                        future.completeExceptionally(new RuntimeException("Failed to get current user: " + error.message));
                    }
            ));
            log.info("✓ GetMe send() registered");
        } catch (Exception e) {
            log.error("✗✗✗ EXCEPTION in getSavedMessagesChatId", e);
            future.completeExceptionally(e);
        }

        log.info("→ Returning CompletableFuture (may not be completed yet)");
        return future;
    }

    private void updateStatusInOriginalChat(long chatId, long messageId, String text) {
        updateStatus(chatId, messageId, text);
    }

    private void deleteMessageDelayed(long chatId, long messageId) {
        new Thread(() -> {
            try {
                Thread.sleep((long) 2000);
                deleteMessage(chatId, messageId);
            } catch (InterruptedException e) {
                log.warn("Delete message delay interrupted", e);
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void handleMessageContent(long chatId, long messageId, TdApi.Message messageSource, TdApi.Chat chat) {
        try {
            if (chat.hasProtectedContent) {
                handleProtectedContent(chatId, messageId, messageSource);
            } else {
                forwardUnprotectedContent(chatId, messageId, messageSource);
            }
        } catch (Exception e) {
            log.error("Error handling message content", e);
            updateStatus(chatId, messageId, "❌ Error: " + e.getMessage());
        }
    }

    private void handleProtectedContent(long chatId, long messageId, TdApi.Message messageSource) {
        if (messageSource.content instanceof TdApi.MessageText text) {
            sendTextMessage(chatId, messageId, text);
            return;
        }

        int fileId = extractFileId(messageSource.content);
        if (fileId == 0) {
            updateStatus(chatId, messageId, "❌ Media type not supported for stealing.");
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
        log.info("=== DOWNLOAD AND REUPLOAD (LINK MODE) START ===");
        log.info("Target chat ID: {}, File ID: {}", chatId, fileId);

        client.send(new TdApi.DownloadFile(fileId, HIGHEST_PRIORITY, 0, 0, true),
                this.handleResult(result -> {
                    String localPath = result.get().local.path;
                    log.info("✓ File downloaded to: {}", localPath);
                    log.info("  File size: {} bytes", result.get().size);
                    log.info("  File exists: {}", new File(localPath).exists());

                    updateStatus(chatId, messageId, "⬆️ Uploading...");
                    log.info("→ Calling reuploadMedia...");
                    reuploadMedia(chatId, messageId, messageSource, localPath);
                }, error -> {
                    log.error("✗ Download failed: {}", error.message);
                    updateStatus(chatId, messageId, "❌ Download failed: " + error.message);
                }));
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

    private void reuploadMedia(long chatId, long messageId, TdApi.Message originalMessage, String localPath) {
        log.info("=== REUPLOAD MEDIA (LINK MODE) START ===");
        log.info("Chat ID: {}, Message ID: {}, Local path: {}", chatId, messageId, localPath);

        String uploadPath = localPath;
        boolean needsMetadataStrip = originalMessage.content instanceof TdApi.MessagePhoto ||
                originalMessage.content instanceof TdApi.MessageVideo;

        if (needsMetadataStrip) {
            log.info("→ File needs metadata modification to force upload");
            uploadPath = modifyFileForReupload(localPath);
            log.info("✓ Modified file path: {}", uploadPath);
        }

        TdApi.InputFile inputFile = new TdApi.InputFileLocal(uploadPath);
        log.info("✓ Created InputFileLocal with path: {}", uploadPath);

        log.info("→ Calling createInputContent...");
        TdApi.InputMessageContent content = createInputContent(originalMessage.content, inputFile);
        log.info("← Returned from createInputContent");

        if (content == null) {
            log.error("✗ Content is NULL!");
            updateStatus(chatId, messageId, "❌ Failed to create input content (Type Unknown).");
            if (!uploadPath.equals(localPath)) cleanupFile(uploadPath);
            return;
        }

        log.info("✓ Content created: {}", content.getClass().getSimpleName());
        log.info("→ Sending message to chat {}...", chatId);

        String finalUploadPath = uploadPath;
        client.send(new TdApi.SendMessage(chatId, 0, null, null, null, content),
                this.handleResult(
                        result -> {
                            log.info("✓✓✓ Upload SUCCESS!");
                            log.info("  Message ID: {}", result.get().id);
                            deleteMessage(chatId, messageId);
                            cleanupFile(localPath);
                            if (!finalUploadPath.equals(localPath)) cleanupFile(finalUploadPath);
                        },
                        error -> {
                            log.error("✗✗✗ Upload FAILED!");
                            log.error("  Error: {}", error.message);
                            updateStatus(chatId, messageId, "❌ Failed to upload: " + error.message);
                            if (!finalUploadPath.equals(localPath)) cleanupFile(finalUploadPath);
                        }
                ));
        log.info("✓ Send request registered");
    }

    /**
     * Modifies file by creating a copy with stripped metadata to force Telegram to treat it as new
     */
    private String modifyFileForReupload(String originalPath) {
        File original = new File(originalPath);
        if (!original.exists()) return originalPath;

        try {
            // 1. Setup paths
            String fileName = original.getName();
            String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

            // Use random UUID folder to ensure unique path
            File modified = new File(original.getParent() + "/temp/" + UUID.randomUUID() + "/", baseName + "_bypass" + extension);

            // 2. Create directory (Critical)
            if (!modified.getParentFile().exists()) {
                if (!modified.getParentFile().mkdirs()) {
                    log.error("Failed to create directory: {}", modified.getParentFile());
                    return originalPath;
                }
            }

            log.info("  Re-hashing file...");
            log.info("  Source: {} ({} bytes)", originalPath, original.length());

            // 3. Manual Stream Copy (The Nuclear Option)
            // We do not use Files.copy(). We read and write manually.
            try (java.io.FileInputStream fis = new java.io.FileInputStream(original);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(modified)) {

                byte[] buffer = new byte[4096]; // 4KB buffer
                int bytesRead;

                // Copy the actual file content
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }

                // 4. INJECT RANDOM JUNK (The Fix)
                // Append a unique UUID string to the end of the file.
                // This forces the SHA256 hash to be completely new.
                String junkData = "---TDLIB-BYPASS-HASH-" + UUID.randomUUID().toString() + "---";
                fos.write(junkData.getBytes());

                fos.flush(); // Force write to disk
            }

            log.info("  Target: {} ({} bytes)", modified.getAbsolutePath(), modified.length());

            // 5. SANITY CHECK
            // If the new file is not larger, the fix FAILED.
            if (modified.length() <= original.length()) {
                log.error("FATAL: File modification failed! Sizes are identical.");
                return originalPath; // Fallback, though it will likely fail upload
            }

            return modified.getAbsolutePath();

        } catch (Exception e) {
            log.error("Failed to modify file for reupload", e);
            return originalPath;
        }
    }

    private TdApi.InputMessageContent createInputContent(TdApi.MessageContent msgContent, TdApi.InputFile inputFile) {
        TdApi.FormattedText caption = extractCaption(msgContent);

        TdApi.InputMessageContent result = switch (msgContent) {
            case TdApi.MessagePhoto p ->
                    new TdApi.InputMessagePhoto(inputFile, null, null, 0, 0, caption, false, null, false);

            case TdApi.MessageVideo v ->
                    new TdApi.InputMessageVideo(inputFile, null, null, 0, null, v.video.duration,
                            v.video.width, v.video.height, v.video.supportsStreaming, caption, false, null, false);

            case TdApi.MessageDocument d ->
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

            case TdApi.MessageSticker s ->
                    new TdApi.InputMessageSticker(inputFile, null, 0, 0, null);

            default -> null;
        };

        if (result != null) {
            log.info("Created input content: {}", result.getClass().getSimpleName());
            if (inputFile instanceof TdApi.InputFileLocal local) {
                log.info("  Input file path: {}", local.path);
            }
        }

        return result;
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

    /**
     * Helper method to create GenericResultHandler with success and error callbacks
     */
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