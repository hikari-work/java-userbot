package com.yann.demosping.plugin.management;

import com.yann.demosping.annotations.UserBotCommand;
import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;

@Component
public class Mute {

    @UserBotCommand(
            commands = {"mute"},
            description = "Mute A User",
            sudoOnly = true
    )
    public void muteUser(TdApi.UpdateNewMessage message, String args) {

    }
}
