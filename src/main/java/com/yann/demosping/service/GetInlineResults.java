package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class GetInlineResults {

    private final SimpleTelegramClient client;

    public GetInlineResults(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public CompletableFuture<TdApi.InlineQueryResults> inlineQuery(long chatId, long botId, String args) {
        CompletableFuture<TdApi.InlineQueryResults> inlineQueryFuture = new CompletableFuture<>();
        client.send(
                new TdApi.GetInlineQueryResults(botId, chatId, null, args, ""), result -> {
                    if (result.isError()) inlineQueryFuture.completeExceptionally(new RuntimeException(result.getError().message));
                    inlineQueryFuture.complete(result.get());
                }
        );
        return inlineQueryFuture;
    }
}
