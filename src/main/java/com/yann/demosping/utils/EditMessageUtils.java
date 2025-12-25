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
public class EditMessageUtils {

    private final SimpleTelegramClient client;
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;

    public CompletableFuture<TdApi.Message> editMessage(long chatId, long messageId, String text, TdApi.TextParseMode parseMode) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text, parseMode).thenAcceptAsync(formattedText -> client.send(
                new TdApi.EditMessageText(chatId, messageId, null,
                        new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), true)), send -> {
                    if (send.isError()) {
                        TdApi.Error error = send.getError();
                        if (error.code == 429) {
                            messageFuture.completeExceptionally(new SendMessageNotCompleteException("Error Flood Wait", chatId, messageId));
                        }
                        messageFuture.completeExceptionally(new SendMessageNotCompleteException("Error : " + error.message, chatId, messageId));
                    }
                    messageFuture.complete(send.get());
                }
        ));
        return messageFuture;
    }

    public CompletableFuture<TdApi.Message> editMessage(long chatId, long messageId, String text) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text).thenAcceptAsync(formattedText -> client.send(
                new TdApi.EditMessageText(chatId, messageId, null,
                        new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), true)), send -> {
                    if (send.isError()) {
                        TdApi.Error error = send.getError();
                        if (error.code == 429) {
                            messageFuture.completeExceptionally(new SendMessageNotCompleteException("Error Flood Wait", chatId, messageId));
                        }
                        messageFuture.completeExceptionally(new SendMessageNotCompleteException("Error : " + error.message, chatId, messageId));
                    }
                    messageFuture.complete(send.get());
                }
        ));
        return messageFuture;
    }
}