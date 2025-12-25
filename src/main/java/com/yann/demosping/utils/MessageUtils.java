package com.yann.demosping.utils;

import com.yann.demosping.exceptions.GetMessageException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class MessageUtils {

    private final SimpleTelegramClient client;

    public MessageUtils(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public CompletableFuture<TdApi.Message> getMessage(long chatId, long messageId, long currentMessageId) {
        CompletableFuture<TdApi.Message> messageFuture = new CompletableFuture<>();
        client.send(
                new TdApi.GetMessage(chatId, messageId), result -> {
                    if (result.isError()) {
                        if (result.getError().code == 429) {
                            messageFuture.completeExceptionally(new GetMessageException("Too Many Request", chatId, currentMessageId));
                        }
                        messageFuture.completeExceptionally(new GetMessageException("Error : " + result.getError().message, chatId, currentMessageId));
                    }
                    messageFuture.complete(result.get());
                }
        );
        return messageFuture;
    }

}
