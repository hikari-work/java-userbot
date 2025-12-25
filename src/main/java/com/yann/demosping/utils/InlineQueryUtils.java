package com.yann.demosping.utils;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class InlineQueryUtils {


    private final SimpleTelegramClient client;

    public InlineQueryUtils(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }
    public CompletableFuture<TdApi.InlineQueryResults> getInlineQueryResult(
            long botId,
            String query,
            long chatId,
            String offset) {
        CompletableFuture<TdApi.InlineQueryResults> future = new CompletableFuture<>();
        log.info("Query is {}", query);

        client.send(new TdApi.GetInlineQueryResults(
                botId, chatId, null, query, offset
        ), result -> {
            if (result.isError()) future.completeExceptionally(new RuntimeException());
            future.complete(result.get());
        });
        return future;
    }

    public CompletableFuture<TdApi.Message> sendInlineQueryResult (
            long chatId, long queryId, String resultId, boolean hideViaBot) {
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
        client.send(new TdApi.SendInlineQueryResultMessage(
                chatId, 0, null, null, queryId, resultId, hideViaBot
        ), result -> {
            if (result.isError()) future.completeExceptionally(new RuntimeException());
            future.complete(result.get());
        });
        return future;
    }

}
