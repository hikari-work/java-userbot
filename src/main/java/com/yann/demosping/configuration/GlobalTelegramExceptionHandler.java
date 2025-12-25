package com.yann.demosping.configuration;

import com.yann.demosping.exceptions.*;
import com.yann.demosping.utils.EditMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class GlobalTelegramExceptionHandler {

    public final EditMessageUtils editMessageUtils;

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
            default -> {
            }
        }

    }
    private void handleTextFormatMessageException(TelegramException ex) {
        if (ex.getChatId() != null && ex.getMessageId() != null) {
            String errorMessage = "Format text not valid " + ex.getMessage();
            editMessageUtils.editMessage(ex.getChatId(), ex.getMessageId(), errorMessage)
                    .exceptionally(editError -> {
                        log.error("Failed Edit Messae {}", editError.getMessage());
                        return null;
                    });
        }
    }
    private void handleSendMessageException(TelegramException ex) {
        if (ex.getChatId() != null && ex.getMessageId() != null) {
            String errorMessage = "❌ Gagal mengirim pesan!\n\n" + ex.getMessage();

            editMessageUtils.editMessage(ex.getChatId(), ex.getMessageId(), errorMessage)
                    .exceptionally(editError -> {
                        log.error("Failed to edit message: {}", editError.getMessage());
                        return null;
                    });
        }
    }

    private void handleEditMessageException(TelegramException ex) {
        log.error("Edit message failed for chat {} message {}: {}",
                ex.getChatId(), ex.getMessageId(), ex.getMessage());
    }

}
