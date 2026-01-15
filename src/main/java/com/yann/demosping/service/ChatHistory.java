package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ChatHistory {

    private final SimpleTelegramClient client;
    public ChatHistory(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }
    public CompletableFuture<TdApi.Messages> getMessages(long chatId, int count, int offset, long start) {
        CompletableFuture<TdApi.Messages> messagesFuture = new CompletableFuture<>();
        client.send(
                new TdApi.GetChatHistory(chatId, start, offset, count, false), messages -> {
                    if (messages.isError()) messagesFuture.completeExceptionally(new RuntimeException(messages.getError().message));
                    messagesFuture.complete(messages.get());
                }
        );
        return messagesFuture;
    }
}
