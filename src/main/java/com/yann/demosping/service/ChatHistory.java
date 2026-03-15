package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ChatHistory {

    private final SimpleTelegramClient client;

    public ChatHistory(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public Mono<TdApi.Messages> getMessages(long chatId, int count, int offset, long start) {
        return Mono.create(sink ->
                client.send(new TdApi.GetChatHistory(chatId, start, offset, count, false), messages -> {
                    if (messages.isError()) sink.error(new RuntimeException(messages.getError().message));
                    else sink.success(messages.get());
                })
        );
    }
}
