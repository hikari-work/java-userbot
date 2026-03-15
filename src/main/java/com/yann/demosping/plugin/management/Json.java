package com.yann.demosping.plugin.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature; // Tambahan untuk pretty print
import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.service.ChatService;
import com.yann.demosping.service.EditMessage;
import com.yann.demosping.service.MessagesService;
import com.yann.demosping.service.OutputPaste;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class Json {

    private final ObjectMapper mapper = new ObjectMapper();
    private final MessagesService messagesService;
    private final EditMessage editMessage;
    private final ChatService chatService;
    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    private static final int MAX_TELEGRAM_LENGTH = 4000;
    private final OutputPaste outputPaste;

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
            messagesService.getMessage(chatId, replyTo.messageId, messageId)
                    .flatMap(message -> {
                        String jsonString = getInfo(message);
                        String safeText = truncateSafe(jsonString);
                        return editMessage.editMessage(chatId, messageId, "<code>" + safeText + "</code>");
                    })
                    .doOnError(globalTelegramExceptionHandler::handle)
                    .subscribe();
        } else {
            chatService.getChatInfo(chatId)
                    .flatMap(chatInfo -> {
                        String jsonString = getInfo(chatInfo);
                        String safeText = truncateSafe(jsonString);
                        return editMessage.editMessage(chatId, messageId, "Pasting JSON to output paste service...")
                                .then(outputPaste.post(safeText))
                                .flatMap(url -> editMessage.editMessage(chatId, messageId, "<code>" + safeText + "</code>"));
                    })
                    .doOnError(globalTelegramExceptionHandler::handle)
                    .subscribe();
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
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException err) {
            return "Error parsing JSON: " + err.getMessage();
        }
    }
}