package com.yann.demosping.utils;

import com.yann.demosping.exceptions.GetMessageException;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ChatUtils {

    private final SimpleTelegramClient client;

    public CompletableFuture<TdApi.Chat> getChatInfo(long chatId) {
        CompletableFuture<TdApi.Chat> getChatInfo = new CompletableFuture<>();
        client.send(
                new TdApi.GetChat(chatId), result -> {
                    if (result.isError()) {
                        getChatInfo.completeExceptionally(new GetMessageException("Error Getting Chat", chatId, 0L));
                    }
                    getChatInfo.complete(result.get());
                }
        );
        return getChatInfo;
    }

}
