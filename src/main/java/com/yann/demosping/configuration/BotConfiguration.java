package com.yann.demosping.configuration;

import com.yann.demosping.bot.manager.InlineBotDispatcher;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
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
                                       @Qualifier("bot") TDLibSettings settings) {
        SimpleTelegramClientFactory simpleTelegramClientFactory = new SimpleTelegramClientFactory();
        return simpleTelegramClientFactory.builder(settings).build(supplier);
    }
    @Bean
    public ApplicationRunner botRunner(@Qualifier("botClient") SimpleTelegramClient client, InlineBotDispatcher inlineBotDispatcher) {
        return args -> {
            log.info("Bot Created");
            client.addUpdateHandler(TdApi.UpdateNewInlineQuery.class, inlineBotDispatcher::dispatch);
        };
    }
}
