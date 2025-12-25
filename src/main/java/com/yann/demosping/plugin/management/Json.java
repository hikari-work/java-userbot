package com.yann.demosping.plugin.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature; // Tambahan untuk pretty print
import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.utils.ChatUtils;
import com.yann.demosping.utils.EditMessageUtils;
import com.yann.demosping.utils.MessageUtils;
import com.yann.demosping.utils.SendMessageUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Json {

    private final SimpleTelegramClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MessageUtils messageUtils;
    private final SendMessageUtils sendMessageUtils;
    private final EditMessageUtils editMessageUtils;
    private final ChatUtils chatUtils;
    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    private static final int MAX_TELEGRAM_LENGTH = 4000;

    @UserBotCommand(
            commands = {"json"},
            description = "Get JSON info of message or chat",
            sudoOnly = true
    )
    public void json(TdApi.UpdateNewMessage update, String args) {
        long messageId = update.message.id;
        long chatId = update.message.chatId;

        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        if (update.message.replyTo instanceof TdApi.MessageReplyToMessage replyTo) {
            messageUtils.getMessage(chatId, replyTo.messageId, messageId).thenAcceptAsync(message -> {
                String jsonString = getInfo(message);
                String safeText = truncateSafe(jsonString);

                editMessageUtils.editMessage(chatId, messageId, "<blockquote expandable>" + safeText + "</blockquote>");
            }).exceptionally(ex -> {
                globalTelegramExceptionHandler.handle(ex);
                return null;
            });
        } else {
            chatUtils.getChatInfo(chatId).thenAcceptAsync(chatInfo -> {
                String jsonString = getInfo(chatInfo);
                String safeText = truncateSafe(jsonString);

                editMessageUtils.editMessage(chatId, messageId, "<blockquote expandable>" + safeText + "</blockquote>");
            }).exceptionally(ex -> {
                globalTelegramExceptionHandler.handle(ex);
                return null;
            });
        }
    }
    private String truncateSafe(String text) {
        if (text == null) return "Error: Object is null";

        if (text.length() > MAX_TELEGRAM_LENGTH) {
            return text.substring(0, MAX_TELEGRAM_LENGTH) + "\n\n... (Truncated)";
        } else {
            return text;
        }
    }

    public String getInfo(Object object) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException err) {
            return "Error parsing JSON: " + err.getMessage();
        }
    }
}