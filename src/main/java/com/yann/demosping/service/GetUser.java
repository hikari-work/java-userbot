package com.yann.demosping.service;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GetUser {

    private final SimpleTelegramClient client;

    public GetUser(@Qualifier("userBotClient") SimpleTelegramClient client) {
        this.client = client;
    }

    public Mono<TdApi.User> getUser(long userId) {
        return Mono.create(sink ->
                client.send(new TdApi.GetUser(userId), user -> {
                    if (user.isError()) sink.error(new RuntimeException(user.getError().message));
                    else sink.success(user.get());
                })
        );
    }
}
