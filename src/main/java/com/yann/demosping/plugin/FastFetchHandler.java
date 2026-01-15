package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.ShellExecutors;
import com.yann.demosping.service.EditMessage;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FastFetchHandler {

    private final ShellExecutors executors;
    private final EditMessage editMessage;

    @UserBotCommand(commands = "fastfetch", description = "", sudoOnly = true)
    public void fastFetch(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        editMessage.editMessage(chatId, messageId, "Fetching...")
                .thenAcceptAsync(sendMessage -> {
                    executors.execute("fastfetch")
                            .thenAccept(result -> {
                                String formatted = "<code>\n" + result + "\n</code>";
                                editMessage.editMessage(chatId, messageId, formatted);
                            });
                });
    }

}
