package com.yann.demosping.service;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OutputPaste {

    private final WebClient webClient;
    private final static String BASE_URL = "https://paste.rs/";

    public OutputPaste(WebClient.Builder webClient) {
        this.webClient = webClient
                .baseUrl(BASE_URL)
                .build();
    }

    public Mono<String> post(String text) {
        return webClient.post()
                .bodyValue(text)
                .retrieve()
                .onStatus(httpStatusCode -> !httpStatusCode.is2xxSuccessful(), response -> Mono.error(new RuntimeException("Error posting to paste.rs")))
                .bodyToMono(String.class);
    }
}
