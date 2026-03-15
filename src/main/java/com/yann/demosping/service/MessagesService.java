package com.yann.demosping.service;

import com.yann.demosping.exceptions.GetMessageException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class MessagesService {

    private final SimpleTelegramClient client;

    public MessagesService(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public Mono<TdApi.Message> getMessage(long chatId, long messageId, long currentMessageId) {
        return Mono.create(sink ->
                client.send(new TdApi.GetMessage(chatId, messageId), result -> {
                    if (result.isError()) {
                        if (result.getError().code == 429) {
                            sink.error(new GetMessageException("Too Many Request", chatId, currentMessageId));
                        } else {
                            sink.error(new GetMessageException("Error : " + result.getError().message, chatId, currentMessageId));
                        }
                    } else {
                        sink.success(result.get());
                    }
                })
        );
    }
}
