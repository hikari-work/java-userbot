package com.yann.demosping.configuration;

import com.yann.demosping.bot.manager.CallbackDispatcher;
import com.yann.demosping.manager.Dispatcher;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
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
public class UserBotConfiguration {

    @Value("${phone.number}")
    private String phoneNumber;

    @Value("${api.hash}")
    private String apiHash;

    @Value("${api.id}")
    private Integer apiId;

    @Bean("userBot")
    public TDLibSettings setSettings() {
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
        APIToken token = new APIToken(apiId, apiHash);
        TDLibSettings settings = TDLibSettings.create(token);
        Path path = Paths.get("tdlib-user-bot");
        settings.setDatabaseDirectoryPath(path.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(path.resolve("downloads"));
        return settings;
    }

    @Bean("userBotSupplier")
    public AuthenticationSupplier<?> authentication() {
        return AuthenticationSupplier.user(phoneNumber);
    }

    @Bean("userBotClient")
    public SimpleTelegramClient clientBuilder(@Qualifier("userBot") TDLibSettings settings,
                                              @Qualifier("userBotSupplier") AuthenticationSupplier<?> authenticationSupplier) {

        SimpleTelegramClientFactory simpleTelegramClientFactory = new SimpleTelegramClientFactory();
        return simpleTelegramClientFactory.builder(settings).build(authenticationSupplier);
    }

    @Bean
    public ApplicationRunner runner(@Qualifier("userBotClient") SimpleTelegramClient client, Dispatcher dispatcher, CallbackDispatcher callbackDispatcher) {
        return args -> {
            client.addUpdateHandler(TdApi.UpdateNewMessage.class, dispatcher::onUpdateMessage);
            client.addUpdateHandler(TdApi.UpdateNewCallbackQuery.class, callbackDispatcher::dispatch);
        };
    }
}