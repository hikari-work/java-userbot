package com.yann.demosping.manager;

import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@Slf4j
@Component
public class Dispatcher {

    private final CommandRegistry commandRegistry;
    private final List<MessageInterceptor> interceptors;

    @Value("${message.prefix}")
    private String prefix;

    public Dispatcher(CommandRegistry commandRegistry, List<MessageInterceptor> interceptors) {
        this.commandRegistry = commandRegistry;
        this.interceptors = interceptors;
    }

    @Async
    public void onUpdateMessage(TdApi.UpdateNewMessage message) {
        String text = extractText(message);

        for (MessageInterceptor interceptor : interceptors) {
            try {
                if (!interceptor.preHandle(message, text)) {
                    return;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (!(message.message.content instanceof TdApi.MessageText textContent)) {
            return;
        }

        String rawText = textContent.text.text;
        if (!rawText.startsWith(prefix)) {
            return;
        }

        String[] parts = rawText.split(" ", 2);
        String trigger = parts[0].replace(prefix, "");
        CommandContainer container = commandRegistry.getCommand(trigger);
        if (container == null) {
            return;
        }

        if (container.command().sudoOnly() && !message.message.isOutgoing) {
            return;
        }

        try {
            container.method().invoke(container.bean(), message, parts.length > 1 ? parts[1] : "");
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private String extractText(TdApi.UpdateNewMessage message) {
        if (message.message.content instanceof TdApi.MessageText text) {
            return text.text.text;
        }
        return "";
    }
}
