package com.yann.demosping.service;

import com.yann.demosping.exceptions.GetMessageException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ChatService {

    private final SimpleTelegramClient client;

    public ChatService(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public Mono<TdApi.Chat> getChatInfo(long chatId) {
        return Mono.create(sink ->
                client.send(new TdApi.GetChat(chatId), result -> {
                    if (result.isError()) {
                        sink.error(new GetMessageException("Error Getting Chat", chatId, 0L));
                    } else {
                        sink.success(result.get());
                    }
                })
        );
    }
}
