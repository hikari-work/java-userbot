package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.service.ModuleStateService;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.service.EditMessage;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class Afk {

    private final ModuleStateService moduleStateService;
    private final EditMessage editMessage;
    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;

    @UserBotCommand(commands = {"afk"}, description = """
            Fitur ini untuk setting AFK saja
            """, sudoOnly = true)
    public void afk(TdApi.UpdateNewMessage message, String args) {
        long chatId = message.message.chatId;
        long messageId = message.message.id;
        Map<String, String> param = ArgsParser.parse(args);
        String reason;

        if (param.containsKey("reason")) {
            reason = param.get("reason");
        } else if (param.containsKey("r")) {
            reason = param.get("r");
        } else if (args != null && !args.isBlank()) {
            reason = args;
        } else {
            reason = "Away From Keyboard";
        }

        moduleStateService.setAfk(true, reason);
        editMessage.editMessage(chatId, messageId, "Memulai AFK Mode Karena <code>" + reason + "</code>")
                        .exceptionally(ex -> {
                            globalTelegramExceptionHandler.handle(ex);
                            return null;
                        });
    }
}
