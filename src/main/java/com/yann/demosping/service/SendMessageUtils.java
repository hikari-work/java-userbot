package com.yann.demosping.service;

import com.yann.demosping.exceptions.SendMessageNotCompleteException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class SendMessageUtils {


    private final SimpleTelegramClient client;
    private final ParseTextEntitiesUtils parseTextEntitiesUtils;

    public SendMessageUtils(@Qualifier("userBotClient") SimpleTelegramClient client, ParseTextEntitiesUtils parseTextEntitiesUtils) {
        this.client = client;
        this.parseTextEntitiesUtils = parseTextEntitiesUtils;
    }

    public CompletableFuture<TdApi.Message> sendMessage(long chatId, long messageId, String text) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        parseTextEntitiesUtils.formatText(text).thenAcceptAsync(formattedText -> client.send(
                new TdApi.SendMessage(chatId, 0, null, null, null, new TdApi.InputMessageText(formattedText, new TdApi.LinkPreviewOptions(), false)), message -> {
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
    public CompletableFuture<TdApi.Message> sendInlineQueryResult(long chatId, long replyToMessageId, long queryId, String resultId) {
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();

        TdApi.InputMessageReplyToMessage replyTo = null;
        if (replyToMessageId != 0) {
            replyTo = new TdApi.InputMessageReplyToMessage(replyToMessageId, null, 0);
        }

        TdApi.SendInlineQueryResultMessage request = new TdApi.SendInlineQueryResultMessage();
        request.chatId = chatId;
        request.messageThreadId = 0;
        request.replyTo = replyTo;
        request.options = null;

        request.queryId = queryId;
        request.resultId = resultId;

        request.hideViaBot = false;

        client.send(request, result -> {
            if (result.isError()) {
                future.completeExceptionally(new RuntimeException("Error " + result.getError().code + ": " + result.getError().message));
            } else {
                future.complete(result.get());
            }
        });

        return future;
    }
    public CompletableFuture<TdApi.Message> sendMessage(long chatId, long replyToMessageId,
                                                        String text, TdApi.ReplyMarkup replyMarkup) {
        TdApi.InputMessageText inputMessage = new TdApi.InputMessageText();
        inputMessage.text = new TdApi.FormattedText(text, new TdApi.TextEntity[0]);

        TdApi.SendMessage sendMessage = new TdApi.SendMessage();
        sendMessage.chatId = chatId;
        sendMessage.inputMessageContent = inputMessage;
        sendMessage.replyMarkup = replyMarkup;

        if (replyToMessageId != 0) {
            sendMessage.replyTo = new TdApi.InputMessageReplyToMessage(replyToMessageId, null, 0);
        }

        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
        client.send(sendMessage, result -> {
            if (result.isError()) {
                future.completeExceptionally(new RuntimeException(result.getError().message));
            } else {
                future.complete(result.get());
            }
        });

        return future;
    }
}
