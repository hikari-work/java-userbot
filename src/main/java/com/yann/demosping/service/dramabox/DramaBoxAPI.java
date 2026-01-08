package com.yann.demosping.service.dramabox;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class DramaBoxAPI {

    private final WebClient webClient;
    private final static String BASE_URL = "https://dramabox.sansekai.my.id/api/dramabox";

    public DramaBoxAPI(WebClient.Builder webClientBuilder) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("dramabox-pool")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofSeconds(60))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS))
                );

        this.webClient = webClientBuilder
                .baseUrl(BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public CompletableFuture<List<DramaBoxSearchResult>> getDramaBoxSearch(String query) {
        log.info("Searching DramaBox for: {}", query);

        return webClient.get()
                .uri(urlBuilder -> urlBuilder
                        .path("/search")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .bodyToFlux(DramaBoxSearchResult.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(throwable -> {
                            boolean shouldRetry = throwable instanceof ClosedChannelException
                                    || throwable instanceof IOException
                                    || throwable instanceof TimeoutException;

                            if (shouldRetry) {
                                log.warn("Retrying request due to: {}", throwable.getMessage());
                            }
                            return shouldRetry;
                        })
                        .doBeforeRetry(signal ->
                                log.info("Retry attempt #{} for query: {}",
                                        signal.totalRetries() + 1, query)
                        )
                )
                .collectList()
                .doOnSuccess(results ->
                        log.info("Successfully retrieved {} results for: {}", results.size(), query)
                )
                .doOnError(error ->
                        log.error("Failed to search DramaBox for query '{}': {}",
                                query, error.getMessage())
                )
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("HTTP Error {}: {}", ex.getStatusCode(), ex.getMessage());
                    return Mono.just(List.of());
                })
                .onErrorResume(Exception.class, ex -> {
                    log.error("Unexpected error: ", ex);
                    return Mono.just(List.of());
                })
                .toFuture();
    }
}