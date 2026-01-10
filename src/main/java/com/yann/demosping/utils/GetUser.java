package com.yann.demosping.utils;

import io.netty.util.concurrent.CompleteFuture;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class GetUser {

    private final SimpleTelegramClient client;
    public GetUser(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public CompletableFuture<TdApi.User> getUser(long userId) {
        CompletableFuture<TdApi.User> future = new CompletableFuture<>();
        client.send(
                new TdApi.GetUser(userId), user -> {
                    if (user.isError()) future.completeExceptionally(new RuntimeException(user.getError().message));
                    else future.complete(user.get());
                }
        );
        return future;

    }
}
