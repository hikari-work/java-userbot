package com.yann.demosping.service;

import com.google.gson.Gson;
import com.yann.demosping.dto.GcastConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GcastStateService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Gson gson;

    private static final String SESSION_PREFIX = "gcast:session:";
    private static final String AWAIT_CRON_PREFIX = "gcast:awaitcron:";
    private static final String WHITELIST_KEY = "gcast:whitelist";
    private static final String BLACKLIST_KEY = "gcast:blacklist";
    private static final String LABEL_PREFIX = "gcast:label:";
    private static final String CANCEL_PREFIX = "gcast:cancel:";
    private static final String RUNNING_KEY = "gcast:running";

    public void saveSession(String sid, GcastConfig config) {
        redisTemplate.opsForValue().set(SESSION_PREFIX + sid, gson.toJson(config), 48, TimeUnit.HOURS);
    }

    public GcastConfig getSession(String sid) {
        Object val = redisTemplate.opsForValue().get(SESSION_PREFIX + sid);
        if (val == null) return null;
        return gson.fromJson(val.toString(), GcastConfig.class);
    }

    public void deleteSession(String sid) {
        redisTemplate.delete(SESSION_PREFIX + sid);
    }

    public void setAwaitingCron(long chatId, String sid) {
        redisTemplate.opsForValue().set(AWAIT_CRON_PREFIX + chatId, sid, 30, TimeUnit.MINUTES);
    }

    public String getAwaitingCron(long chatId) {
        Object val = redisTemplate.opsForValue().get(AWAIT_CRON_PREFIX + chatId);
        return val == null ? null : val.toString();
    }

    public void clearAwaitingCron(long chatId) {
        redisTemplate.delete(AWAIT_CRON_PREFIX + chatId);
    }

    public void addWhitelist(long chatId) {
        redisTemplate.opsForSet().add(WHITELIST_KEY, String.valueOf(chatId));
    }

    public void removeWhitelist(long chatId) {
        redisTemplate.opsForSet().remove(WHITELIST_KEY, String.valueOf(chatId));
    }

    public Set<Long> getWhitelist() {
        return getLongSet(WHITELIST_KEY);
    }

    public void addBlacklist(long chatId) {
        redisTemplate.opsForSet().add(BLACKLIST_KEY, String.valueOf(chatId));
    }

    public void removeBlacklist(long chatId) {
        redisTemplate.opsForSet().remove(BLACKLIST_KEY, String.valueOf(chatId));
    }

    public Set<Long> getBlacklist() {
        return getLongSet(BLACKLIST_KEY);
    }

    public void addLabel(String name, long chatId) {
        redisTemplate.opsForSet().add(LABEL_PREFIX + name, String.valueOf(chatId));
    }

    public void removeLabel(String name, long chatId) {
        redisTemplate.opsForSet().remove(LABEL_PREFIX + name, String.valueOf(chatId));
    }

    public Set<Long> getLabel(String name) {
        return getLongSet(LABEL_PREFIX + name);
    }

    public Set<String> getLabelNames() {
        Set<String> keys = redisTemplate.keys(LABEL_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return new HashSet<>();
        return keys.stream()
                .map(k -> k.substring(LABEL_PREFIX.length()))
                .collect(Collectors.toSet());
    }

    public void setCancelFlag(String sid) {
        redisTemplate.opsForValue().set(CANCEL_PREFIX + sid, "1", 1, TimeUnit.HOURS);
    }

    public boolean isCancelled(String sid) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CANCEL_PREFIX + sid));
    }

    public void clearCancelFlag(String sid) {
        redisTemplate.delete(CANCEL_PREFIX + sid);
    }

    public void addRunningSession(String sid) {
        redisTemplate.opsForSet().add(RUNNING_KEY, sid);
    }

    public void removeRunningSession(String sid) {
        redisTemplate.opsForSet().remove(RUNNING_KEY, sid);
    }

    public Set<String> getRunningSessionIds() {
        Set<Object> raw = redisTemplate.opsForSet().members(RUNNING_KEY);
        if (raw == null) return new HashSet<>();
        return raw.stream().map(Object::toString).collect(Collectors.toSet());
    }

    private Set<Long> getLongSet(String key) {
        Set<Object> raw = redisTemplate.opsForSet().members(key);
        if (raw == null) return new HashSet<>();
        Set<Long> result = new HashSet<>();
        for (Object o : raw) {
            try {
                result.add(Long.parseLong(o.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }
}
