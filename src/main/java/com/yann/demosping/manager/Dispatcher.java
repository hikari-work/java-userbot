package com.yann.demosping.manager;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@Slf4j
@Component
public class Dispatcher {

    private final SimpleTelegramClient client;

    private final CommandRegistry commandRegistry;

    public Dispatcher(@Qualifier("userBotClient") SimpleTelegramClient client,
                      CommandRegistry commandRegistry,
                      List<BotInterceptor> interceptors) {
        this.client = client;
        this.commandRegistry = commandRegistry;
        this.interceptors = interceptors;
    }

    private final List<BotInterceptor> interceptors;

    @Value("${message.prefix}")
    private String prefix;

    @Async
    public void onUpdateMessage(TdApi.UpdateNewMessage message) {

        if (message.message.content instanceof TdApi.MessageText textContent) {
            long chatId;
            if (message.message.senderId instanceof TdApi.MessageSenderUser user) {
                chatId = user.userId;
            } else if (message.message.chatId > 0) {
                chatId = message.message.chatId;
            } else {
                chatId = 0L;
            }
            for (BotInterceptor interceptor : interceptors) {
                try {
                    boolean proceed = interceptor.preHandle(message, textContent.text.text);
                    if (!proceed) {
                        return;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (textContent.text.text.startsWith(prefix)) {
                String[] parts = textContent.text.text.split(" ", 2);
                String trigger = parts[0].replace(prefix, "");
                CommandContainer container = commandRegistry.getCommand(trigger);
                if (container != null) {
                    if (commandRegistry.getCommand(trigger).command().sudoOnly()) {

                        if (chatId == 0L && client.getMe().id != chatId) {
                            return;
                        }
                    }
                    try {
                        container.method().invoke(container.bean(), message, parts.length > 1 ? parts[1] : "");
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
