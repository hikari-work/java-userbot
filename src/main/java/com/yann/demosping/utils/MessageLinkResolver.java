package com.yann.demosping.utils; // Sesuaikan package Anda

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageLinkResolver {

    private final SimpleTelegramClient client;

    public CompletableFuture<TdApi.Message> resolve(String link) {
        CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
        String cleanLink = link.trim();

        log.info("Resolving link via GetMessageLinkInfo: {}", cleanLink);

        // FITUR SAKTI: Biarkan TDLib yang menerjemahkan Link -> Message Object
        client.send(new TdApi.GetMessageLinkInfo(cleanLink), result -> {
            if (result.isError()) {
                log.error("Link Info Error: {}", result.getError().message);
                future.completeExceptionally(new RuntimeException(result.getError().message));
                return;
            }

            TdApi.MessageLinkInfo info = result.get();

            // Cek apakah TDLib berhasil menemukan pesannya
            if (info.message != null) {
                // SUKSES! Kita dapat Internal ID yang benar
                log.info("Message Resolved! ChatID: {}, MsgID: {} (Internal)", info.chatId, info.message.id);
                future.complete(info.message);
            } else {
                // Link valid, tapi pesan tidak tertangkap (misal: bot belum join, atau pesan private)
                // Info.chatId biasanya tetap ada jika chatnya public/known
                if (info.chatId != 0) {
                    // Coba fetch manual jika info.message null (jarang terjadi jika sudah join)
                    // Tapi karena kita tidak tahu Internal ID-nya, kita tidak bisa GetMessage manual.
                    // Kita lempar error agar user tahu bot tidak bisa akses pesan spesifik itu.
                    future.completeExceptionally(new RuntimeException("Chat ditemukan (ID: " + info.chatId + "), tapi Pesan tidak dapat diakses/belum terload."));
                } else {
                    future.completeExceptionally(new RuntimeException("Link valid tapi tidak mengarah ke pesan yang bisa diakses bot."));
                }
            }
        });

        return future;
    }
}