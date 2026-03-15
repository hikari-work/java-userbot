package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GetInlineResults {

    private final SimpleTelegramClient client;

    public GetInlineResults(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public Mono<TdApi.InlineQueryResults> inlineQuery(long chatId, long botId, String args) {
        return Mono.create(sink ->
                client.send(new TdApi.GetInlineQueryResults(botId, chatId, null, args, ""), result -> {
                    if (result.isError()) sink.error(new RuntimeException(result.getError().message));
                    else sink.success(result.get());
                })
        );
    }
}
