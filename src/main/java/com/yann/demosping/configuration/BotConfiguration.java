package com.yann.demosping.configuration;

import com.yann.demosping.bot.manager.CallbackDispatcher;
import com.yann.demosping.bot.manager.InlineBotDispatcher;
import com.yann.demosping.dto.GcastConfig;
import com.yann.demosping.service.GcastStateService;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class BotConfiguration {

    @Value("${bot.token}")
    private String botToken;

    @Value("${api.hash}")
    private String apiHash;

    @Value("${api.id}")
    private Integer apiId;

    @Bean("bot")
    public TDLibSettings settings() {
        APIToken token = new APIToken(apiId, apiHash);
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
        TDLibSettings settings = TDLibSettings.create(token);
        Path path = Paths.get("tdlib-bot");
        settings.setDatabaseDirectoryPath(path.resolve("bot-data"));
        settings.setDownloadedFilesDirectoryPath(path.resolve("bot-downloads"));
        return settings;
    }
    @Bean("botSupplier")
    public AuthenticationSupplier<?> supplier() {
        return AuthenticationSupplier.bot(botToken);
    }
    @Bean("botClient")
    public SimpleTelegramClient runner(@Qualifier("botSupplier") AuthenticationSupplier<?> supplier,
                                       @Qualifier("bot") TDLibSettings settings,
                                       InlineBotDispatcher inlineBotDispatcher,
                                       CallbackDispatcher callbackDispatcher,
                                       GcastStateService gcastStateService) {
        log.info("Creating Client");
        SimpleTelegramClientFactory simpleTelegramClientFactory = new SimpleTelegramClientFactory();
        log.info("Building Client");
        SimpleTelegramClientBuilder builder = simpleTelegramClientFactory.builder(settings);

        log.info("Bot Building");

        builder.addUpdateHandler(TdApi.UpdateNewInlineQuery.class, inlineBotDispatcher::dispatch);

        builder.addUpdateHandler(TdApi.UpdateNewChosenInlineResult.class, result -> {
            log.info("Chosen inline result: query='{}' resultId='{}' inlineMessageId='{}'",
                    result.query, result.resultId, result.inlineMessageId);
            if (result.resultId.startsWith("wizard_")) {
                String sid = result.resultId.substring(7);
                GcastConfig cfg = gcastStateService.getSession(sid);
                if (cfg != null) {
                    cfg.controlInlineMessageId = result.inlineMessageId;
                    gcastStateService.saveSession(sid, cfg);
                }
            }
        });

        builder.addUpdateHandler(TdApi.UpdateNewInlineCallbackQuery.class, callbackDispatcher::dispatchInline);

        // Regular callback queries from buttons on bot-sent messages (e.g., gcast panel)
        builder.addUpdateHandler(TdApi.UpdateNewCallbackQuery.class, callbackDispatcher::dispatchBotCallback);

        return builder.build(supplier);
    }
}
