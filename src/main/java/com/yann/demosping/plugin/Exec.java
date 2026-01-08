package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.ShellExecutors;
import com.yann.demosping.utils.EditMessageUtils;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class Exec {

    private final ShellExecutors shellExecutors;
    private final EditMessageUtils editMessageUtils;

    public Exec(ShellExecutors shellExecutors, EditMessageUtils editMessageUtils) {
        this.shellExecutors = shellExecutors;
        this.editMessageUtils = editMessageUtils;
    }

    @UserBotCommand(commands = "exec", description = "", sudoOnly = true)
    public void exec(TdApi.UpdateNewMessage message, String args) {
        shellExecutors.execute(args)
                    .thenAccept(result -> {
                        String text = "<code>" + (result.length() > 2000 ? result.substring(0, 200) : result) + "</code>";
                        editMessageUtils.editMessage(message.message.chatId, message.message.id, text);
                    });

    }
}
