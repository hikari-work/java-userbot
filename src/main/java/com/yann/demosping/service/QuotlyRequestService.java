package com.yann.demosping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yann.demosping.dto.QuotlyRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;

@Slf4j
@Service
public class QuotlyRequestService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private static final String API_URL = "https://bot.lyo.su/quote/generate";

    public QuotlyRequestService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl(API_URL)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    public Mono<byte[]> generateStickerAsync(QuotlyRequest request) {
        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("❌ HTTP ERROR {}: {}", response.statusCode(), errorBody);
                            return Mono.error(new RuntimeException("HTTP " + response.statusCode() + ": " + errorBody));
                        })
                )
                .bodyToMono(String.class)
                .flatMap(jsonString -> {
                    try {
                        // 1. Parse JSON Response
                        QuotlyResponse response = objectMapper.readValue(jsonString, QuotlyResponse.class);

                        // 2. Cek apakah ada result di dalam 'result.image' (Format Baru Lyo.su)
                        String base64Image = null;

                        if (response.getResult() != null && response.getResult().getImage() != null) {
                            base64Image = response.getResult().getImage();
                        } else if (response.getImage() != null) {
                            // Fallback untuk format lama (langsung di root)
                            base64Image = response.getImage();
                        }

                        if (base64Image == null || base64Image.isEmpty()) {
                            log.error("❌ API Response OK but Image Missing: {}", jsonString);
                            return Mono.error(new RuntimeException("No image found in API response"));
                        }

                        // 3. Decode Base64
                        byte[] decoded = Base64.getDecoder().decode(base64Image);
                        return Mono.just(decoded);

                    } catch (Exception e) {
                        log.error("❌ JSON Parse Error: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }

    @Data
    @NoArgsConstructor
    public static class QuotlyResponse {
        private boolean ok;
        private QuotlyResult result;

        private String image;
        private String message;

        @Data
        @NoArgsConstructor
        public static class QuotlyResult {
            private String image;
            private String type;
            private int width;
            private int height;
        }
    }
}