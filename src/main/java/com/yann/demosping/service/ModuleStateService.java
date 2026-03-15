package com.yann.demosping.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModuleStateService {

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    private static final String KEY_PREFIX = "userbot:";

    public Mono<Boolean> saveFilter(long chatId, String trigger, String content) {
        return reactiveRedisTemplate.opsForHash().put(KEY_PREFIX + "filters:" + chatId, trigger, content);
    }

    public Mono<Map<Object, Object>> getAllFilters(long chatId) {
        return reactiveRedisTemplate.opsForHash().entries(KEY_PREFIX + "filters:" + chatId)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    public Mono<Long> deleteFilter(long chatId, String trigger) {
        return reactiveRedisTemplate.opsForHash().remove(KEY_PREFIX + "filters:" + chatId, trigger);
    }

    public Mono<Long> addTarget(String moduleName, long id) {
        return reactiveRedisTemplate.opsForSet().add(KEY_PREFIX + moduleName, id);
    }

    public Mono<Long> removeTarget(String moduleName, long id) {
        return reactiveRedisTemplate.opsForSet().remove(KEY_PREFIX + moduleName, id);
    }

    public Mono<Boolean> isTarget(String moduleName, long id) {
        return reactiveRedisTemplate.opsForSet().isMember(KEY_PREFIX + moduleName, id)
                .defaultIfEmpty(false);
    }

    public Mono<Set<Long>> getAllTargets(String moduleName) {
        return reactiveRedisTemplate.opsForSet().members(KEY_PREFIX + moduleName)
                .map(obj -> {
                    try {
                        return Long.valueOf(obj.toString());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public Mono<Void> setAfk(boolean status, String reason) {
        if (status) {
            return Mono.when(
                    reactiveRedisTemplate.opsForValue().set(KEY_PREFIX + "afk:status", "true"),
                    reactiveRedisTemplate.opsForValue().set(KEY_PREFIX + "afk:reason", reason),
                    reactiveRedisTemplate.opsForValue().set(KEY_PREFIX + "afk:from", System.currentTimeMillis())
            );
        } else {
            return Mono.when(
                    reactiveRedisTemplate.delete(KEY_PREFIX + "afk:status"),
                    reactiveRedisTemplate.delete(KEY_PREFIX + "afk:reason"),
                    reactiveRedisTemplate.delete(KEY_PREFIX + "afk:from")
            );
        }
    }

    public Mono<Boolean> isAfk() {
        return reactiveRedisTemplate.hasKey(KEY_PREFIX + "afk:status")
                .defaultIfEmpty(false);
    }

    public Mono<String> getAfkReason() {
        return reactiveRedisTemplate.opsForValue().get(KEY_PREFIX + "afk:reason")
                .map(Object::toString)
                .defaultIfEmpty("");
    }

    public Mono<String> getAfkDuration() {
        return reactiveRedisTemplate.opsForValue().get(KEY_PREFIX + "afk:from")
                .map(afkTime -> {
                    long afkFromTimeMillis = Long.parseLong(afkTime.toString());
                    Duration duration = Duration.between(Instant.ofEpochMilli(afkFromTimeMillis), Instant.now());
                    long second = duration.getSeconds();
                    long hours = second / 3600;
                    long minutes = (second % 3600) / 60;
                    long seconds = second % 60;
                    StringBuilder builder = new StringBuilder();
                    if (hours > 0) builder.append(hours).append(" Jam ");
                    if (minutes > 0) builder.append(minutes).append(" Menit ");
                    builder.append(seconds).append(" detik");
                    return builder.toString().trim();
                })
                .defaultIfEmpty("0 second");
    }
}
