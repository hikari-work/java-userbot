package com.yann.demosping.utils;

import com.yann.demosping.exceptions.SendMessageNotCompleteException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendMessageUtils {

    private final SimpleTelegramClient client;
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;

    public CompletableFuture<TdApi.Message> sendMessage(long chatId, long messageId, String text, TdApi.TextParseMode parseMode) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text, parseMode).thenAcceptAsync(formattedText -> client.send(new TdApi.SendMessage(
                chatId, messageId, null, null, null, new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), false)
        ), msgResult -> {
            if (msgResult.isError()) {
                TdApi.Error error = msgResult.getError();
                if (error.code == 429) {
                    messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message, Flood Wait", chatId, messageId));
                } if (error.code == 400) {
                    messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message, User Error", chatId, messageId));
                }
                messageFuture.completeExceptionally(new SendMessageNotCompleteException("Unknown Error", chatId, messageId));
            }

        })).exceptionally(throwable -> {
            log.error("Error");
            messageFuture.completeExceptionally(throwable);
            return null;
        });
        return messageFuture;
    }
    public CompletableFuture<TdApi.Message> sendMessage(long chatId, String text, TdApi.TextParseMode parseMode, TdApi.InputMessageContent content) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text, parseMode).thenAcceptAsync(formattedText -> client.send(new TdApi.SendMessage(
                chatId, 0, null, null, null, content
        ), msgResult -> {
            if (msgResult.isError()) {
                messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message", chatId, 0L));
            }

        })).exceptionally(throwable -> {
            log.error("Error Sending Message");
            messageFuture.completeExceptionally(throwable);
            return null;
        });
        return messageFuture;
    }
    public CompletableFuture<TdApi.Message> sendMessage(long chatId, long messageId, String text, TdApi.InputMessageContent content) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text).thenAcceptAsync(formattedText -> client.send(new TdApi.SendMessage(
                chatId, messageId, null, null, null, content
        ), msgResult -> {
            if (msgResult.isError()) {
                messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message " + msgResult.getError().message, chatId, messageId));
            }

        })).exceptionally(throwable -> {
            log.error("Error Send");
            messageFuture.completeExceptionally(throwable);
            return null;
        });
        return messageFuture;
    }
    public CompletableFuture<TdApi.Message> sendMessage(long chatId, long messageId, String text) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text).thenAcceptAsync(formattedText -> client.send(
                new TdApi.SendMessage(chatId, messageId, null, null, null, new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), false)), message -> {
                    if (message.isError()) {
                        TdApi.Error error = message.getError();
                        if (error.code == 429) {
                            messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message FloodWait", chatId, messageId));
                        }
                        messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message" + error.message, chatId, messageId));
                    }
                    messageFuture.complete(message.get());
                }
        ));
        return messageFuture;
    }

    public CompletableFuture<TdApi.Message> sendMessage(long chatId, String text) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text).thenAcceptAsync(formattedText -> client.send(
                new TdApi.SendMessage(chatId, 0, null, null, null, new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), false)), message -> {
                    if (message.isError()) {
                        TdApi.Error error = message.getError();
                        if (error.code == 429) {
                            messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message FloodWait", chatId, 0L));
                        }
                        messageFuture.completeExceptionally(new SendMessageNotCompleteException("Cannot Send Message" + error.message, chatId, 0L));
                    }
                    messageFuture.complete(message.get());
                }
        ));
        return messageFuture;
    }
}
