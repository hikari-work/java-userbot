package com.yann.demosping.configuration;

import com.yann.demosping.manager.Dispatcher;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class BotConfiguration {

    @Value("${phone.number}")
    private String phoneNumber;

    @Value("${api.hash}")
    private String apiHash;

    @Value("${api.id}")
    private Integer apiId;

    @Bean
    public TDLibSettings setSettings() {
        Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());
        APIToken token = new APIToken(apiId, apiHash);
        TDLibSettings settings = TDLibSettings.create(token);
        Path path = Paths.get("tdlib-user-bot");
        settings.setDatabaseDirectoryPath(path.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(path.resolve("downloads"));
        return settings;
    }

    @Bean
    public AuthenticationSupplier<?> authentication() {
        return AuthenticationSupplier.user(phoneNumber);
    }

    @Bean
    public SimpleTelegramClient clientBuilder(TDLibSettings settings,
                                              AuthenticationSupplier<?> authenticationSupplier) {

        SimpleTelegramClientFactory simpleTelegramClientFactory = new SimpleTelegramClientFactory();
        return simpleTelegramClientFactory.builder(settings).build(authenticationSupplier);
    }

    @Bean
    public ApplicationRunner runner(SimpleTelegramClient client, Dispatcher dispatcher) {
        log.info("Bot berjalan. Menunggu pesan masuk...");
        return args -> {
            log.info("TDLight Client siap. Mendaftarkan handler...");
            client.addUpdateHandler(TdApi.UpdateNewMessage.class, dispatcher::onUpdateMessage);
        };
    }
}