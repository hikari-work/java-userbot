package com.yann.demosping.service;

import com.google.gson.Gson;
import com.yann.demosping.dto.GcastConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GcastStateService {

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final Gson gson;

    private static final String SESSION_PREFIX = "gcast:session:";
    private static final String AWAIT_CRON_PREFIX = "gcast:awaitcron:";
    private static final String WHITELIST_KEY = "gcast:whitelist";
    private static final String BLACKLIST_KEY = "gcast:blacklist";
    private static final String LABEL_PREFIX = "gcast:label:";
    private static final String CANCEL_PREFIX = "gcast:cancel:";
    private static final String RUNNING_KEY = "gcast:running";

    public Mono<Boolean> saveSession(String sid, GcastConfig config) {
        return reactiveRedisTemplate.opsForValue()
                .set(SESSION_PREFIX + sid, gson.toJson(config), Duration.ofHours(48));
    }

    public Mono<GcastConfig> getSession(String sid) {
        return reactiveRedisTemplate.opsForValue().get(SESSION_PREFIX + sid)
                .map(val -> gson.fromJson(val.toString(), GcastConfig.class));
    }

    public Mono<Long> deleteSession(String sid) {
        return reactiveRedisTemplate.delete(SESSION_PREFIX + sid);
    }

    public Mono<Boolean> setAwaitingCron(long chatId, String sid) {
        return reactiveRedisTemplate.opsForValue()
                .set(AWAIT_CRON_PREFIX + chatId, sid, Duration.ofMinutes(30));
    }

    public Mono<String> getAwaitingCron(long chatId) {
        return reactiveRedisTemplate.opsForValue().get(AWAIT_CRON_PREFIX + chatId)
                .map(Object::toString);
    }

    public Mono<Long> clearAwaitingCron(long chatId) {
        return reactiveRedisTemplate.delete(AWAIT_CRON_PREFIX + chatId);
    }

    public Mono<Long> addWhitelist(long chatId) {
        return reactiveRedisTemplate.opsForSet().add(WHITELIST_KEY, String.valueOf(chatId));
    }

    public Mono<Long> removeWhitelist(long chatId) {
        return reactiveRedisTemplate.opsForSet().remove(WHITELIST_KEY, String.valueOf(chatId));
    }

    public Mono<Set<Long>> getWhitelist() {
        return getLongSet(WHITELIST_KEY);
    }

    public Mono<Long> addBlacklist(long chatId) {
        return reactiveRedisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(chatId));
    }

    public Mono<Long> removeBlacklist(long chatId) {
        return reactiveRedisTemplate.opsForSet().remove(BLACKLIST_KEY, String.valueOf(chatId));
    }

    public Mono<Set<Long>> getBlacklist() {
        return getLongSet(BLACKLIST_KEY);
    }

    public Mono<Long> addLabel(String name, long chatId) {
        return reactiveRedisTemplate.opsForSet().add(LABEL_PREFIX + name, String.valueOf(chatId));
    }

    public Mono<Long> removeLabel(String name, long chatId) {
        return reactiveRedisTemplate.opsForSet().remove(LABEL_PREFIX + name, String.valueOf(chatId));
    }

    public Mono<Set<Long>> getLabel(String name) {
        return getLongSet(LABEL_PREFIX + name);
    }

    public Mono<Set<String>> getLabelNames() {
        return reactiveRedisTemplate.keys(LABEL_PREFIX + "*")
                .map(k -> k.substring(LABEL_PREFIX.length()))
                .collect(Collectors.toSet())
                .defaultIfEmpty(new HashSet<>());
    }

    public Mono<Boolean> setCancelFlag(String sid) {
        return reactiveRedisTemplate.opsForValue()
                .set(CANCEL_PREFIX + sid, "1", Duration.ofHours(1));
    }

    public Mono<Boolean> isCancelled(String sid) {
        return reactiveRedisTemplate.hasKey(CANCEL_PREFIX + sid)
                .defaultIfEmpty(false);
    }

    public Mono<Long> clearCancelFlag(String sid) {
        return reactiveRedisTemplate.delete(CANCEL_PREFIX + sid);
    }

    public Mono<Long> addRunningSession(String sid) {
        return reactiveRedisTemplate.opsForSet().add(RUNNING_KEY, sid);
    }

    public Mono<Long> removeRunningSession(String sid) {
        return reactiveRedisTemplate.opsForSet().remove(RUNNING_KEY, sid);
    }

    public Mono<Set<String>> getRunningSessionIds() {
        return reactiveRedisTemplate.opsForSet().members(RUNNING_KEY)
                .map(Object::toString)
                .collect(Collectors.toSet())
                .defaultIfEmpty(new HashSet<>());
    }

    private Mono<Set<Long>> getLongSet(String key) {
        return reactiveRedisTemplate.opsForSet().members(key)
                .map(o -> {
                    try {
                        return Long.parseLong(o.toString());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .defaultIfEmpty(new HashSet<>());
    }
}
