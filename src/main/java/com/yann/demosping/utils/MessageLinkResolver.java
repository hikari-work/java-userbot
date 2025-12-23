package com.yann.demosping.utils;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageLinkResolver {

    private final SimpleTelegramClient client;

    @Async
    public CompletableFuture<TdApi.Message> resolve(String link) {
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
        String cleanLink = link.trim();

        client.send(new TdApi.GetMessageLinkInfo(cleanLink), result -> {
            if (result.isError()) {

                future.completeExceptionally(new RuntimeException(result.getError().message));
                return;
            }

            TdApi.MessageLinkInfo info = result.get();

            if (info.message != null) {

                future.complete(info.message);
            } else {

                if (info.chatId != 0) {
                    future.completeExceptionally(new RuntimeException("Chat ditemukan (ID: " + info.chatId + "), tapi Pesan tidak dapat diakses/belum terload."));
                } else {
                    future.completeExceptionally(new RuntimeException("Link valid tapi tidak mengarah ke pesan yang bisa diakses bot."));
                }
            }
        });

        return future;
    }
}