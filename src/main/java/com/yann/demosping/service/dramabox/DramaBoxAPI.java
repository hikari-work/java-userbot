package com.yann.demosping.service.dramabox;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DramaBoxAPI {

    private final WebClient webClient;
    private final static String BASE_URL = "https://dramabox.sansekai.my.id/api/dramabox/";

    public DramaBoxAPI(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    public CompletableFuture<List<DramaBoxSearchResult>> getDramaBoxSearch(String query) {
        return webClient.get()
                .uri(urlBuilder -> urlBuilder
                        .path("/search")
                        .queryParam("query", query).build())
                .retrieve()
                .bodyToFlux(DramaBoxSearchResult.class)
                .collectList()
                .toFuture();

    }
}
