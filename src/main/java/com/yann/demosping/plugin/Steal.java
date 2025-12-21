package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.utils.CopyMessageUtils;
import com.yann.demosping.utils.MessageLinkResolver;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.Optional;
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
            description = "Steal protected content from link",
            sudoOnly = true
    )
    public void stealContent(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;

        Optional<String> link = extractLink(args);
        if (link.isEmpty()) {
            updateStatus(chatId, messageId, "❌ Link not found. Use: <code>/steal -l https://t.me/c/..</code>");
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
                    .exceptionally(e -> handleError(chatId, messageId, e));
        });
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
        client.send(new TdApi.DownloadFile(fileId, HIGHEST_PRIORITY, 0, 0, true), result -> {
            if (result.isError()) {
                updateStatus(chatId, messageId, "❌ Download failed: " + result.getError().message);
                return;
            }

            String localPath = result.get().local.path;
            updateStatus(chatId, messageId, "⬆️ Uploading...");
            reuploadMedia(chatId, messageId, messageSource, localPath);
        });
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
        TdApi.InputFile inputFile = new TdApi.InputFileLocal(localPath);
        TdApi.InputMessageContent content = createInputContent(originalMessage.content, inputFile);

        if (content == null) {
            updateStatus(chatId, messageId, "❌ Failed to create input content (Type Unknown).");
            return;
        }

        client.send(new TdApi.SendMessage(chatId, 0, null, null, null, content), result -> {
            if (result.isError()) {
                updateStatus(chatId, messageId, "❌ Failed to upload: " + result.getError().message);
            } else {
                deleteMessage(chatId, messageId);
                cleanupFile(localPath);
            }
        });
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
        client.send(new TdApi.GetChat(chatId), result -> {
            if (result.isError()) {
                future.completeExceptionally(new RuntimeException("Chat Not Found"));
            } else {
                future.complete(result.get());
            }
        });
        return future;
    }

    private Void handleError(long chatId, long messageId, Throwable e) {
        log.error("Failed to get chat info", e);
        updateStatus(chatId, messageId, "❌ " + "Failed to get chat info" + ": " + e.getMessage());
        return null;
    }
}