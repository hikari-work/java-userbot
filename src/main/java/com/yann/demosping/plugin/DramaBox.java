package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.utils.ArgsParser;
import com.yann.demosping.utils.InlineQueryUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class DramaBox {

    private final InlineQueryUtils inlineQueryUtils;
    @Value("${bot.token}")
    private String botToken;

    private final SimpleTelegramClient client;

    public DramaBox(@Qualifier("userBotClient") SimpleTelegramClient client, InlineQueryUtils inlineQueryUtils) {
        this.client = client;
        this.inlineQueryUtils = inlineQueryUtils;
    }

    @UserBotCommand(commands = {"dramabox"},
            description = "Dramabox",
            sudoOnly = true
    )
    public void dramabox(TdApi.UpdateNewMessage update, String args) {
        Map<String, String> param = ArgsParser.parse(args);
        long chatId = update.message.chatId;

        long botUserId = Long.parseLong(botToken.split(":")[0]);
        String query = "dramabox " + param.getOrDefault("s", "");
        client.send(new TdApi.DeleteMessages(chatId, new long[]{update.message.id}, true));

        inlineQueryUtils.getInlineQueryResult(botUserId, query, chatId, "")
                .thenCompose(resultInline -> {
                    if (resultInline.results.length == 0) {
                        log.warn("Tidak ada hasil ditemukan untuk query: {}", query);
                        return CompletableFuture.completedFuture(null);
                    }

                    TdApi.InlineQueryResult result = resultInline.results[0];
                    String resultId = "";
                    if (result instanceof TdApi.InlineQueryResultArticle article) {
                        resultId = article.id;
                    } else if (result instanceof TdApi.InlineQueryResultPhoto photo) {
                        resultId = photo.id;

                    }

                    log.info("QueryID: {}, ResultID: {}", resultInline.inlineQueryId, resultId);

                    return inlineQueryUtils.sendInlineQueryResult(
                            chatId,
                            resultInline.inlineQueryId,
                            resultId,
                            false
                    );
                })
                .exceptionally(ex -> {
                    log.error("Error sending inline result", ex);
                    return null;
                });
    }
}
