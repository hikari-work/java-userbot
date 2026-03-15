package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class MessageLinkResolver {

    private final SimpleTelegramClient client;

    public MessageLinkResolver(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public Mono<TdApi.Message> resolve(String link) {
        String cleanLink = link.trim();
        return Mono.create(sink ->
                client.send(new TdApi.GetMessageLinkInfo(cleanLink), result -> {
                    if (result.isError()) {
                        sink.error(new RuntimeException(result.getError().message));
                        return;
                    }
                    TdApi.MessageLinkInfo info = result.get();
                    if (info.message != null) {
                        sink.success(info.message);
                    } else {
                        if (info.chatId != 0) {
                            sink.error(new RuntimeException("Chat ditemukan (ID: " + info.chatId + "), tapi Pesan tidak dapat diakses/belum terload."));
                        } else {
                            sink.error(new RuntimeException("Link valid tapi tidak mengarah ke pesan yang bisa diakses bot."));
                        }
                    }
                })
        );
    }
}
