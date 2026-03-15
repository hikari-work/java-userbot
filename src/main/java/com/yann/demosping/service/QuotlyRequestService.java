package com.yann.demosping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yann.demosping.dto.QuotlyRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Base64;

@Slf4j
@Service
public class QuotlyRequestService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private static final String BASE_URL = "https://bot.lyo.su/quote";

    public QuotlyRequestService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Generate sticker via /generate — returns decoded image bytes.
     * Response is base64-encoded JSON: {"ok":true,"result":{"image":"..."}}
     */
    public Mono<byte[]> generateStickerAsync(QuotlyRequest request) {
        return webClient.post()
                .uri("/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            log.error("HTTP {} from Quotly API: {}", response.statusCode(), body);
                            return Mono.error(new QuotlyException("HTTP " + response.statusCode() + ": " + body));
                        })
                )
                .bodyToMono(String.class)
                .flatMap(json -> {
                    try {
                        QuotlyResponse response = objectMapper.readValue(json, QuotlyResponse.class);

                        if (response.getError() != null) {
                            String msg = switch (response.getError()) {
                                case "query_empty" -> "Request is empty";
                                case "messages_empty" -> "Messages array is missing";
                                case "empty_messages" -> "No valid messages to render";
                                default -> "API error: " + response.getError();
                            };
                            log.error("Quotly API error: {}", response.getError());
                            return Mono.error(new QuotlyException(msg));
                        }

                        if (!response.isOk() && response.getMessage() != null) {
                            log.error("Quotly API not ok: {}", response.getMessage());
                            return Mono.error(new QuotlyException(response.getMessage()));
                        }

                        String base64Image = null;
                        if (response.getResult() != null && response.getResult().getImage() != null) {
                            base64Image = response.getResult().getImage();
                        } else if (response.getImage() != null) {
                            base64Image = response.getImage();
                        }

                        if (base64Image == null || base64Image.isEmpty()) {
                            log.error("Quotly API returned no image. Full response: {}", json);
                            return Mono.error(new QuotlyException("No image in API response"));
                        }

                        return Mono.just(Base64.getDecoder().decode(base64Image));

                    } catch (Exception e) {
                        log.error("Failed to parse Quotly API response: {}", json, e);
                        return Mono.error(new QuotlyException("Failed to parse API response: " + e.getMessage()));
                    }
                })
                .onErrorMap(WebClientResponseException.class,
                        ex -> new QuotlyException("Network error: " + ex.getMessage()));
    }

    /**
     * Generate sticker via /generate.webp — returns raw binary directly (no base64).
     * Slightly more efficient than /generate.
     */
    public Mono<byte[]> generateDirect(QuotlyRequest request) {
        String ext = "webp".equals(request.getFormat()) ? "webp" : "png";
        return webClient.post()
                .uri("/generate." + ext)
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            log.error("HTTP {} from Quotly direct API: {}", response.statusCode(), body);
                            return Mono.error(new QuotlyException("HTTP " + response.statusCode() + ": " + body));
                        })
                )
                .bodyToMono(byte[].class)
                .onErrorMap(WebClientResponseException.class,
                        ex -> new QuotlyException("Network error: " + ex.getMessage()));
    }

    public static class QuotlyException extends RuntimeException {
        public QuotlyException(String message) {
            super(message);
        }
    }

    @Data
    @NoArgsConstructor
    public static class QuotlyResponse {
        private boolean ok;
        private String error;
        private String message;
        private QuotlyResult result;
        private String image;

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
