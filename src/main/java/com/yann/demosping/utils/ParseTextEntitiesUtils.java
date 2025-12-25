package com.yann.demosping.utils;

import com.yann.demosping.exceptions.FormatTextNotValidException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ParseTextEntitiesUtils {

    private final SimpleTelegramClient client;

    public CompletableFuture<TdApi.FormattedText> formatText(String text, TdApi.TextParseMode parseMode ) {
        CompletableFuture<TdApi.FormattedText> textFuture = new CompletableFuture<>();
        client.send(
                new TdApi.ParseTextEntities(text, parseMode), result -> {
                    if (result.isError()) {
                        TdApi.Object object = result.get();
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
                        TdApi.Object object = result.get();
                        textFuture.completeExceptionally(new FormatTextNotValidException("Text Not Valid", 0L,0L));
                    }
                    textFuture.complete(result.get());
                }
        );
        return textFuture;
    }
}
