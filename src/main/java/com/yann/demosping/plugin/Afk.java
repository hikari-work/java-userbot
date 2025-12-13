package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.service.ModuleStateService;
import com.yann.demosping.utils.ArgsParser;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class Afk {

    private final SimpleTelegramClient client;
    private final ModuleStateService moduleStateService;

    @UserBotCommand(commands = {"afk"}, description = """
            Fitur ini untuk setting AFK
            """)
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
        client.send(
                new TdApi.ParseTextEntities(
                        "Memulai AFK Mode Karena <code>" + reason + "</code>", new TdApi.TextParseModeHTML()
                ), result -> {
                    if (result.isError()) {
                        return;
                    }
                    client.send(
                            new TdApi.EditMessageText(
                                    chatId, messageId, null,
                                    new TdApi.InputMessageText(
                                            result.get(), new TdApi.LinkPreviewOptions(), false
                                    )
                            )
                    );
                }
        );
    }
}
