package com.yann.demosping.configuration;

import com.yann.demosping.exceptions.*;
import com.yann.demosping.service.EditMessage;
import com.yann.demosping.service.SendMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalTelegramExceptionHandler {

    public final EditMessage editMessage;
    private final SendMessageUtils sendMessageUtils;

    public void handle(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;

        if (!(cause instanceof TelegramException ex)) {
            log.error("Unhandled Exception {}", cause.getMessage(), cause);
            return;
        }
        switch (ex) {
            case FormatTextNotValidException ignored -> handleTextFormatMessageException(ex);
            case SendMessageNotCompleteException ignored -> handleSendMessageException(ex);
            case EditMessageNotCompleteException ignored -> handleEditMessageException(ex);
            case GetMessageException ignored -> handleEditMessageException(ex);
            default -> {
            }
        }

    }
    private void handleTextFormatMessageException(TelegramException ex) {
        if (ex.getChatId() != null && ex.getMessageId() != null) {
            String errorMessage = "Format text not valid " + ex.getMessage();
            editMessage.editMessage(ex.getChatId(), ex.getMessageId(), errorMessage)
                    .doOnError(editError -> log.error("Failed Edit Message {}", editError.getMessage()))
                    .subscribe();
        }
    }

    private void handleSendMessageException(TelegramException ex) {
        if (ex.getChatId() != null && ex.getMessageId() != null) {
            String errorMessage = "❌ Gagal mengirim pesan!\n\n" + ex.getMessage();

            editMessage.editMessage(ex.getChatId(), ex.getMessageId(), errorMessage)
                    .onErrorResume(editError -> sendMessageUtils.sendMessage(ex.getChatId(), 0, errorMessage))
                    .subscribe();
        }
    }

    private void handleEditMessageException(TelegramException ex) {
        log.error("Edit message failed for chat {} message {}: {}",
                ex.getChatId(), ex.getMessageId(), ex.getMessage());
    }

}
