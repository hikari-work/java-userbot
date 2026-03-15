package com.yann.demosping.service;

import com.yann.demosping.exceptions.FormatTextNotValidException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ParseTextEntitiesUtils {

    private final SimpleTelegramClient client;

    public ParseTextEntitiesUtils(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public Mono<TdApi.FormattedText> formatText(String text, TdApi.TextParseMode parseMode) {
        return Mono.create(sink ->
                client.send(new TdApi.ParseTextEntities(text, parseMode), result -> {
                    if (result.isError()) {
                        String errorMsg = result.getError() != null ? result.getError().message : "Unknown Error";
                        sink.error(new FormatTextNotValidException("Text Not Valid: " + errorMsg, 0L, 0L));
                        return;
                    }
                    sink.success(result.get());
                })
        );
    }

    public Mono<TdApi.FormattedText> formatText(String text) {
        return Mono.create(sink ->
                client.send(new TdApi.ParseTextEntities(text, new TdApi.TextParseModeHTML()), result -> {
                    if (result.isError()) {
                        String errorMsg = result.getError() != null ? result.getError().message : "Unknown Error";
                        sink.error(new FormatTextNotValidException("Text Not Valid: " + errorMsg, 0L, 0L));
                        return;
                    }
                    sink.success(result.get());
                })
        );
    }
}
