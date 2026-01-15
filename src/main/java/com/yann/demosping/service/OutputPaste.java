package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OutputPaste {

    private final WebClient webClient;
    private final static String BASE_URL = "https://paste.rs/";
    private final SimpleTelegramClient client;

    public OutputPaste(WebClient.Builder webClient, @Qualifier("botClient") SimpleTelegramClient client) {
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();
        this.client = client;
    }

    public Mono<String> post(String text) {
        return webClient.post()
                .bodyValue(text)
                .retrieve()
                .onStatus(httpStatusCode -> !httpStatusCode.is2xxSuccessful(), response -> Mono.error(new RuntimeException("Error posting to paste.rs")))
                .bodyToMono(String.class);
    }
}
