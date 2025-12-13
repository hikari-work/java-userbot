package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class Info {

    @Value("${user.id}")
    private Long userId;

    private final SimpleTelegramClient client;

    @UserBotCommand(
            commands = {"info", "whois"},
            description = "Get full user information"
    )
    public void getUserInfoHandler(TdApi.UpdateNewMessage message, String args) {
        if (message.message.senderId instanceof TdApi.MessageSenderUser user) {
            if (user.userId != userId) {
                return;
            }
        }
        long chatId = message.message.chatId;
        client.send(new TdApi.DeleteMessages(chatId, new long[]{message.message.id}, true), null);
        resolveTargetUserId(message, args)
                .thenCompose(userId -> getUserInformation(userId)
                        .thenCompose(user -> generatingTextResult(user)
                                .thenCompose(formattedText -> generateMessageContent(user.id, formattedText)
                .handle((albumContent, ex) -> {
                    if (ex != null || albumContent == null || albumContent.isEmpty()) {
                        sendFallbackText(chatId, formattedText);
                    } else {
                        sendAlbum(chatId, albumContent);
                    }
                    return null;
                })))).exceptionally(ex -> {
            sendError(chatId, "Error: " + ex.getMessage());
            return null;
        });
    }

    private CompletableFuture<Long> resolveTargetUserId(TdApi.UpdateNewMessage message, String args) {
        CompletableFuture<Long> future = new CompletableFuture<>();

        if (args == null || args.trim().isEmpty()) {
            if (message.message.replyTo instanceof TdApi.MessageReplyToMessage replyInfo) {
                client.send(new TdApi.GetMessage(message.message.chatId, replyInfo.messageId), res -> {
                    if (res.isError()) {
                        future.completeExceptionally(new RuntimeException("Pesan yang di-reply tidak ditemukan."));
                        return;
                    }
                    TdApi.Message replyMsg = res.get();
                    if (replyMsg.senderId instanceof TdApi.MessageSenderUser senderUser) {
                        future.complete(senderUser.userId);
                    } else {
                        future.completeExceptionally(new RuntimeException("Pesan ini bukan dari user (mungkin channel/anon)."));
                    }
                });
            } else {
                future.complete(message.message.chatId > 0 ? message.message.chatId : userId);
            }
            return future;
        }

        if (args.startsWith("@")) {
            String username = args.substring(1);
            client.send(new TdApi.SearchPublicChat(username), res -> {
                if (res.isError()) {
                    future.completeExceptionally(new RuntimeException("Username tidak ditemukan."));
                    return;
                }
                TdApi.Chat chat = res.get();
                if (chat.type instanceof TdApi.ChatTypePrivate privateChat) {
                    future.complete(privateChat.userId);
                } else {
                    future.completeExceptionally(new RuntimeException("Username ini bukan milik User Personal."));
                }
            });
            return future;
        }

        if (args.matches("^\\+?\\d+$")) {
            try {
                long userId = Long.parseLong(args.replaceAll("\\+", ""));
                future.complete(userId);
            } catch (NumberFormatException e) {
                future.completeExceptionally(new RuntimeException("Format ID salah."));
            }
            return future;
        }

        future.completeExceptionally(new RuntimeException("Format argumen tidak dikenali."));
        return future;
    }

    private CompletableFuture<TdApi.User> getUserInformation(long userId) {
        CompletableFuture<TdApi.User> future = new CompletableFuture<>();
        client.send(new TdApi.GetUser(userId), result -> {
            if (result.isError()) {
                future.completeExceptionally(new RuntimeException("User ID tidak valid / User belum dikenal bot."));
            } else {
                future.complete(result.get());
            }
        });
        return future;
    }

    private CompletableFuture<TdApi.FormattedText> generatingTextResult(TdApi.User user) {
        CompletableFuture<TdApi.FormattedText> future = new CompletableFuture<>();
        StringBuilder sb = new StringBuilder();

        sb.append("<b>👤 USER INFORMATION</b>\n\n");

        String firstName = user.firstName == null ? "" : user.firstName;
        String lastName = user.lastName == null ? "" : user.lastName;
        String fullName = (firstName + " " + lastName).trim();

        sb.append("<b>ID:</b> <code>").append(user.id).append("</code>\n");
        sb.append("<b>First Name:</b> ").append(escapeHtml(firstName)).append("\n");
        if (!lastName.isEmpty()) {
            sb.append("<b>Last Name:</b> ").append(escapeHtml(lastName)).append("\n");
        }
        sb.append("<b>Full Name:</b> ").append(escapeHtml(fullName)).append("\n");

        if (user.usernames != null && user.usernames.activeUsernames.length > 0) {
            String usernames = Arrays.stream(user.usernames.activeUsernames)
                    .map(u -> "@" + u)
                    .collect(Collectors.joining(", "));
            sb.append("<b>Username:</b> ").append(usernames).append("\n");
        } else {
            sb.append("<b>Username:</b> <i>None</i>\n");
        }

        if (user.phoneNumber != null && !user.phoneNumber.isEmpty()) {
            sb.append("<b>Phone:</b> +").append(user.phoneNumber).append("\n");
        }

        sb.append("<b>Status:</b> ").append(getUserStatus(user.status)).append("\n");
        sb.append("<b>Premium:</b> ").append(user.isPremium ? "✅ Yes" : "❌ No").append("\n");

        // Cek Bot / Scam
        if (user.type instanceof TdApi.UserTypeBot) sb.append("<b>Type:</b> 🤖 Bot\n");

        client.send(new TdApi.ParseTextEntities(sb.toString(), new TdApi.TextParseModeHTML()), res -> {
            if (res.isError()) {
                future.complete(new TdApi.FormattedText(sb.toString(), null));
            } else {
                future.complete(res.get());
            }
        });
        return future;
    }

    private CompletableFuture<List<TdApi.InputMessageContent>> generateMessageContent(long userId, TdApi.FormattedText caption) {
        CompletableFuture<List<TdApi.InputMessageContent>> future = new CompletableFuture<>();

        client.send(new TdApi.GetUserProfilePhotos(userId, 0, 10), photoResult -> {
            if (photoResult.isError()) {
                future.complete(null);
                return;
            }

            TdApi.ChatPhotos photos = photoResult.get();

            if (photos.totalCount == 0 || photos.photos.length == 0) {
                future.complete(null);
                return;
            }

            List<TdApi.InputMessageContent> albumContent = new ArrayList<>();
            for (int i = 0; i < photos.photos.length; i++) {
                TdApi.ChatPhoto photo = photos.photos[i];
                TdApi.File file = photo.sizes[photo.sizes.length - 1].photo;

                TdApi.InputFile inputFile = new TdApi.InputFileRemote(file.remote.id);
                TdApi.FormattedText photoCaption = (i == 0) ? caption : null;

                albumContent.add(new TdApi.InputMessagePhoto(
                        inputFile, null, null, 0, 0, photoCaption, false, null, false
                ));
            }
            future.complete(albumContent);
        });
        return future;
    }

    private void sendAlbum(long chatId, List<TdApi.InputMessageContent> content) {
        client.send(new TdApi.SendMessageAlbum(
                chatId,
                0,
                null,
                null,
                content.toArray(new TdApi.InputMessageContent[0])
        ));
    }

    private void sendFallbackText(long chatId, TdApi.FormattedText text) {
        client.send(new TdApi.SendMessage(
                chatId, 0, null, null, null,
                new TdApi.InputMessageText(text, new TdApi.LinkPreviewOptions(), false)
        ));
    }

    private void sendError(long chatId, String message) {
        client.send(new TdApi.SendMessage(
                chatId, 0, null, null, null,
                new TdApi.InputMessageText(new TdApi.FormattedText(message, null), null, false)
        ));
    }

    private String getUserStatus(TdApi.UserStatus status) {
        if (status instanceof TdApi.UserStatusOnline) return "🟢 Online";
        if (status instanceof TdApi.UserStatusOffline) return "⚫ Offline";
        if (status instanceof TdApi.UserStatusRecently) return "🟡 Recently";
        if (status instanceof TdApi.UserStatusLastWeek) return "Last Week";
        if (status instanceof TdApi.UserStatusLastMonth) return "Last Month";
        return "Unknown/Hidden";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}