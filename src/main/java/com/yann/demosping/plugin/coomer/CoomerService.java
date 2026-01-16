package com.yann.demosping.plugin.coomer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Slf4j
@Service
public class CoomerService {

    private final WebClient webClient;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int POSTS_PER_PAGE = 50;
    private final ObjectMapper mapper = new ObjectMapper();

    public CoomerService(WebClient.Builder webClient) {
        this.webClient = webClient
                .baseUrl("https://coomer.st/api/v1")
                .defaultHeader("Accept", "text/css")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        log.info("CoomerService initialized with base URL: https://coomer.st/api/v1");
    }

    public Mono<Set<String>> scrapeAllMedia(String service, String userId) {
        log.info("Starting media scraping for service: {}, userId: {}", service, userId);

        return fetchUserProfile(service, userId)
                .doOnSubscribe(sub -> log.info("Subscribed to fetchUserProfile"))
                .flatMap(profile -> {
                    log.info("Received user profile: {}", profile);
                    int postCount = profile.getPostCount();
                    log.info("User profile fetched successfully. Total posts: {}", postCount);

                    if (postCount == 0) {
                        log.warn("No posts found for user: {}", userId);
                        return Mono.just(new HashSet<>());
                    }

                    List<Integer> offsets = calculateOffsets(postCount);
                    log.info("Calculated {} page offsets to fetch", offsets.size());

                    return Flux.fromIterable(offsets)
                            .flatMap(offset -> fetchPostPage(service, userId, offset)
                                    .doOnSubscribe(sub -> log.info("Fetching posts at offset: {}", offset))
                                    .doOnComplete(() -> log.info("Completed fetching posts at offset: {}", offset)))
                            .collectList()
                            .doOnNext(list -> log.info("Collected {} total paths from all pages", list.size()))
                            .map(HashSet::new)
                            .doOnSuccess(paths -> log.info("Successfully scraped {} media paths", paths.size()));
                });
    }

    private Mono<ProfileDTO> fetchUserProfile(String service, String userId) {
        log.info("Fetching user profile for service: {}, userId: {}", service, userId);

        return webClient.get()
                .uri("/{service}/user/{userId}/profile", service, userId)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        ProfileDTO profile = mapper.readValue(body, ProfileDTO.class);
                        log.info("Profile parsed successfully");
                        return Mono.just(profile);
                    } catch (Exception e) {
                        log.error("Failed to parse profile JSON: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Failed to parse profile response", e));
                    }
                })
                .timeout(REQUEST_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying profile fetch (attempt {}/{}). Reason: {}",
                                        retrySignal.totalRetries() + 1,
                                        MAX_RETRY_ATTEMPTS,
                                        retrySignal.failure().getMessage())))
                .doOnError(WebClientResponseException.class, e ->
                        log.error("HTTP error fetching profile - Status: {}, Body: {}",
                                e.getStatusCode(), e.getResponseBodyAsString()))
                .doOnError(e -> log.error("Error fetching user profile: {}", e.getMessage(), e))
                .onErrorMap(e -> new RuntimeException("Failed to fetch user profile for " + userId, e));
    }

    private Flux<String> fetchPostPage(String service, String userId, int offset) {
        log.info("Fetching post page - service: {}, userId: {}, offset: {}", service, userId, offset);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{service}/user/{userId}/posts")
                        .queryParam("o", offset)
                        .build(service, userId))
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(body -> {
                    try {
                        log.debug("Received raw posts response {}", body);
                        log.debug("Raw posts response received at offset {}, length: {}", offset, body.length());
                        PostDTO[] posts = mapper.readValue(body, PostDTO[].class);
                        log.debug("Parsed {} posts at offset {}", posts.length, offset);
                        return Flux.fromArray(posts);
                    } catch (Exception e) {
                        log.error("Failed to parse posts JSON at offset {}: {}", offset, e.getMessage());
                        return Flux.error(new RuntimeException("Failed to parse posts response", e));
                    }
                })
                .timeout(REQUEST_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying post page fetch at offset {} (attempt {}/{}). Reason: {}",
                                        offset,
                                        retrySignal.totalRetries() + 1,
                                        MAX_RETRY_ATTEMPTS,
                                        retrySignal.failure().getMessage())))
                .flatMap(this::extractMediaPaths)
                .doOnError(WebClientResponseException.class, e ->
                        log.error("HTTP error fetching posts at offset {} - Status: {}, Body: {}",
                                offset, e.getStatusCode(), e.getResponseBodyAsString()))
                .doOnError(e -> log.error("Error fetching post page at offset {}: {}", offset, e.getMessage(), e))
                .onErrorResume(e -> {
                    log.error("Skipping page at offset {} due to error", offset, e);
                    return Flux.empty();
                });
    }

    private Flux<String> extractMediaPaths(PostDTO post) {
        Set<String> paths = new HashSet<>();

        try {
            if (post.getFile() != null && post.getFile().getPath() != null) {
                String filePath = post.getFile().getPath();
                paths.add(filePath);
                log.trace("Extracted file path: {}", filePath);
            }

            if (post.getAttachments() != null) {
                post.getAttachments().forEach(attachment -> {
                    if (attachment != null && attachment.getPath() != null) {
                        paths.add(attachment.getPath());
                        log.trace("Extracted attachment path: {}", attachment.getPath());
                    }
                });
            }

            log.debug("Extracted {} media paths from post", paths.size());
        } catch (Exception e) {
            log.error("Error extracting media paths from post: {}", e.getMessage(), e);
        }

        return Flux.fromIterable(paths);
    }

    private List<Integer> calculateOffsets(int postCount) {
        List<Integer> offsets = IntStream.iterate(0, i -> i < postCount, i -> i + POSTS_PER_PAGE)
                .boxed()
                .toList();
        log.debug("Calculated offsets: {}", offsets);
        return offsets;
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException webClientException) {
            int statusCode = webClientException.getStatusCode().value();
            boolean shouldRetry = statusCode >= 500 || statusCode == 429 || statusCode == 408;

            log.debug("HTTP {} - Retryable: {}", statusCode, shouldRetry);
            return shouldRetry;
        }
        if (throwable.getMessage() != null && throwable.getMessage().contains("Failed to parse")) {
            log.debug("Parse error - NOT retryable");
            return false;
        }

        log.debug("Exception {} is retryable", throwable.getClass().getSimpleName());
        return true;
    }
}