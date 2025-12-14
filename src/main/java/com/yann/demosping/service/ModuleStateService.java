package com.yann.demosping.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModuleStateService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "userbot:";

    private static final String DATA_PREFIX = "data:";

    public void saveFilter(long chatId, String trigger, String content) {
        redisTemplate.opsForHash().put(KEY_PREFIX + "filters:" + chatId, trigger, content);
    }
    public Map<Object, Object> getAllFilters(long chatId) {
        String key = KEY_PREFIX + "filters:" + chatId;
        return redisTemplate.opsForHash().entries(key);
    }
    public boolean deleteFilter(long chatId, String trigger) {
        String key = KEY_PREFIX + "filters:" + chatId;

        return redisTemplate.opsForHash().delete(key, trigger) > 0;
    }

    public void addTarget(String moduleName, long id) {
        redisTemplate.opsForSet().add(KEY_PREFIX + moduleName, id);
    }
    public void removeTarget(String moduleName, long id) {
        redisTemplate.opsForSet().remove(KEY_PREFIX + moduleName, id);
    }
    public boolean isTarget(String moduleName, long id) {
        Boolean result = redisTemplate.opsForSet().isMember(KEY_PREFIX + moduleName, id);
        return result != null && result;
    }
    public Set<Long> getAllTargets(String moduleName) {
        Set<Object> members = redisTemplate.opsForSet().members(KEY_PREFIX + moduleName);
        if (members == null) return Set.of();

        return members.stream()
                .map(obj -> Long.valueOf(obj.toString()))
                .collect(Collectors.toSet());
    }
    public void setAfk(boolean status, String reason) {
        if (status) {
            redisTemplate.opsForValue().set(KEY_PREFIX + "afk:status", "true");
            redisTemplate.opsForValue().set(KEY_PREFIX + "afk:reason", reason);
            redisTemplate.opsForValue().set(KEY_PREFIX + "afk:from", System.currentTimeMillis());
        } else {
            redisTemplate.delete(KEY_PREFIX + "afk:status");
            redisTemplate.delete(KEY_PREFIX + "afk:reason");
            redisTemplate.delete(KEY_PREFIX + "afk:from");
        }
    }

    public boolean isAfk() {
        return redisTemplate.hasKey(KEY_PREFIX + "afk:status");
    }

    public String getAfkReason() {
        Object reason = redisTemplate.opsForValue().get(KEY_PREFIX +  "afk:reason");
        return reason != null ? reason.toString() : "";
    }

    public String getAfkDuration() {
        Object afkTime = redisTemplate.opsForValue().get(KEY_PREFIX + "afk:from");
        long afkFromTimeMillis = afkTime != null ? Long.parseLong(afkTime.toString()) : 0;
        if (afkFromTimeMillis == 0L) {
            return "0 second";
        }
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
    }
}
