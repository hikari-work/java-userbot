package com.yann.demosping.service;

import com.yann.demosping.exceptions.FormatTextNotValidException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ParseTextEntitiesUtils {

    private final SimpleTelegramClient client;

    public ParseTextEntitiesUtils(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public CompletableFuture<TdApi.FormattedText> formatText(String text, TdApi.TextParseMode parseMode ) {
        CompletableFuture<TdApi.FormattedText> textFuture = new CompletableFuture<>();
        client.send(
                new TdApi.ParseTextEntities(text, parseMode), result -> {
                    if (result.isError()) {
                        textFuture.completeExceptionally(new FormatTextNotValidException("Text Not Valid", 0L,0L));
                    }
                    textFuture.complete(result.get());
                }
        );
        return textFuture;
    }
    public CompletableFuture<TdApi.FormattedText> formatText(String text) {
        CompletableFuture<TdApi.FormattedText> textFuture = new CompletableFuture<>();
        client.send(
                new TdApi.ParseTextEntities(text, new TdApi.TextParseModeHTML()), result -> {
                    if (result.isError()) {
                        textFuture.completeExceptionally(new FormatTextNotValidException("Text Not Valid", 0L,0L));
                    }
                    textFuture.complete(result.get());
                }
        );
        return textFuture;
    }
}
