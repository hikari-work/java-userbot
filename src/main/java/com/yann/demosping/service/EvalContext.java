package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Static context holder for JShell eval.
 * Set by EvalPlugin before each evaluation so JShell snippets can access
 * the live TDLib client and current message objects.
 * Available in JShell as:
 *   c       — SimpleTelegramClient (userBotClient)
 *   message — TdApi.UpdateNewMessage that triggered ,eval
 *   reply   — TdApi.Message replied-to (or null)
 *   chatId  — long
 *   msgId   — long
 */
public final class EvalContext {

    public static volatile SimpleTelegramClient c;
    public static volatile TdApi.UpdateNewMessage message;
    public static volatile TdApi.Message reply;
    public static volatile long chatId;
    public static volatile long msgId;

    /**
     * Blocking TDLib send helper — use in eval snippets when you need a result.
     * Example: var me = EvalContext.send(new TdApi.GetMe());
     */
    public static <T extends TdApi.Object> T send(TdApi.Function<T> fn) throws Exception {
        return Mono.<T>create(sink -> c.send(fn, result -> {
            if (result.isError()) sink.error(new RuntimeException(result.getError().message));
            else sink.success(result.get());
        })).block(Duration.ofSeconds(10));
    }

    /** Context snapshot — stored so Re-run can restore the original context. */
    public record Snapshot(TdApi.UpdateNewMessage message, TdApi.Message reply, long chatId, long msgId) {}

    public static Snapshot snapshot() {
        return new Snapshot(message, reply, chatId, msgId);
    }

    public static void restore(Snapshot s) {
        message = s.message();
        reply   = s.reply();
        chatId  = s.chatId();
        msgId   = s.msgId();
    }
}
