package com.yann.demosping.service;

import com.yann.demosping.dto.ImgBBResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ImgBBService {

    private final WebClient webClient;
    private static final String IMGBB_API_KEY = "b2dd90867e234031e9deb529369283e2";

    public ImgBBService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.imgbb.com/1").build();
    }

    public Mono<String> uploadImage(String base64Image) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("image", base64Image);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/upload")
                        .queryParam("key", IMGBB_API_KEY)
                        .queryParam("expiration", 600)
                        .build())
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(ImgBBResponse.class)
                .map(response -> {
                    if (response != null && response.getData() != null) {
                        return response.getData().getUrl();
                    }
                    throw new RuntimeException("ImgBB Upload Failed: Empty Response");
                });
    }
}