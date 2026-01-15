package com.yann.demosping.configuration;

import com.yann.demosping.bot.manager.CallbackDispatcher;
import com.yann.demosping.bot.manager.InlineBotDispatcher;
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
                                       CallbackDispatcher callbackDispatcher) {
        log.info("Creating Client");
        SimpleTelegramClientFactory simpleTelegramClientFactory = new SimpleTelegramClientFactory();
        log.info("Building Client");
        SimpleTelegramClientBuilder builder = simpleTelegramClientFactory.builder(settings);

        log.info("Bot Building");

        builder.addUpdateHandler(TdApi.UpdateNewInlineQuery.class, inlineBotDispatcher::dispatch);

        builder.addUpdateHandler(TdApi.UpdateNewCallbackQuery.class, query -> {
            log.info("=== CALLBACK QUERY RECEIVED ===");
            log.info("Query ID: {}", query.id);
            log.info("Sender User ID: {}", query.senderUserId);
            log.info("Chat ID: {}", query.chatId);
            log.info("Message ID: {}", query.messageId);

            if (query.payload instanceof TdApi.CallbackQueryPayloadData) {
                byte[] data = ((TdApi.CallbackQueryPayloadData) query.payload).data;
                log.info("Payload Data: {}", new String(data));
            }
            log.info("================================");

            callbackDispatcher.dispatch(query);
        });

        return builder.build(supplier);
    }
}
