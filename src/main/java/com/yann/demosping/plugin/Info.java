package com.yann.demosping.plugin;

import com.yann.demosping.annotations.UserBotCommand;
import com.yann.demosping.configuration.GlobalTelegramExceptionHandler;
import com.yann.demosping.service.ParseTextEntitiesUtils;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Info {

    private final ParseTextEntitiesUtils parseTextEntitiesUtils;
    private final GlobalTelegramExceptionHandler globalTelegramExceptionHandler;
    @Value("${user.id}")
    private Long userId;

    private final SimpleTelegramClient client;

    public Info(ParseTextEntitiesUtils parseTextEntitiesUtils,
                GlobalTelegramExceptionHandler globalTelegramExceptionHandler,
                @Qualifier("userBotClient") SimpleTelegramClient client) {
        this.parseTextEntitiesUtils = parseTextEntitiesUtils;
        this.globalTelegramExceptionHandler = globalTelegramExceptionHandler;
        this.client = client;
    }

    @UserBotCommand(
            commands = {"info", "whois"},
            description = "Get full user information",
            sudoOnly = true
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
                .flatMap(uid -> getUserInformation(uid)
                        .flatMap(user -> generatingTextResult(user)
                                .flatMap(formattedText -> generateMessageContent(user.id, formattedText)
                                        .doOnNext(albumContent -> {
                                            if (albumContent.isEmpty()) sendFallbackText(chatId, formattedText);
                                            else sendAlbum(chatId, albumContent);
                                        })
                                )
                        )
                )
                .onErrorResume(ex -> {
                    sendError(chatId, "Error: " + ex.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    private Mono<Long> resolveTargetUserId(TdApi.UpdateNewMessage message, String args) {
        if (args == null || args.trim().isEmpty()) {
            if (message.message.replyTo instanceof TdApi.MessageReplyToMessage replyInfo) {
                return Mono.create(sink ->
                        client.send(new TdApi.GetMessage(message.message.chatId, replyInfo.messageId), res -> {
                            if (res.isError()) {
                                sink.error(new RuntimeException("Pesan yang di-reply tidak ditemukan."));
                                return;
                            }
                            TdApi.Message replyMsg = res.get();
                            if (replyMsg.senderId instanceof TdApi.MessageSenderUser senderUser) {
                                sink.success(senderUser.userId);
                            } else {
                                sink.error(new RuntimeException("Pesan ini bukan dari user (mungkin channel/anon)."));
                            }
                        })
                );
            } else {
                return Mono.just(message.message.chatId > 0 ? message.message.chatId : userId);
            }
        }

        if (args.startsWith("@")) {
            String username = args.substring(1);
            return Mono.create(sink ->
                    client.send(new TdApi.SearchPublicChat(username), res -> {
                        if (res.isError()) {
                            sink.error(new RuntimeException("Username tidak ditemukan."));
                            return;
                        }
                        TdApi.Chat chat = res.get();
                        if (chat.type instanceof TdApi.ChatTypePrivate privateChat) {
                            sink.success(privateChat.userId);
                        } else {
                            sink.error(new RuntimeException("Username ini bukan milik User Personal."));
                        }
                    })
            );
        }

        if (args.matches("^\\+?\\d+$")) {
            try {
                long uid = Long.parseLong(args.replaceAll("\\+", ""));
                return Mono.just(uid);
            } catch (NumberFormatException e) {
                return Mono.error(new RuntimeException("Format ID salah."));
            }
        }

        return Mono.error(new RuntimeException("Format argumen tidak dikenali."));
    }

    private Mono<TdApi.User> getUserInformation(long userId) {
        return Mono.create(sink ->
                client.send(new TdApi.GetUser(userId), result -> {
                    if (result.isError()) sink.error(new RuntimeException("User ID tidak valid / User belum dikenal bot."));
                    else sink.success(result.get());
                })
        );
    }

    private Mono<TdApi.FormattedText> generatingTextResult(TdApi.User user) {
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

        if (user.type instanceof TdApi.UserTypeBot) sb.append("<b>Type:</b> 🤖 Bot\n");

        return parseTextEntitiesUtils.formatText(sb.toString())
                .doOnError(globalTelegramExceptionHandler::handle);
    }

    private Mono<List<TdApi.InputMessageContent>> generateMessageContent(long userId, TdApi.FormattedText caption) {
        return Mono.create(sink ->
                client.send(new TdApi.GetUserProfilePhotos(userId, 0, 10), photoResult -> {
                    if (photoResult.isError()) {
                        sink.success(List.of());
                        return;
                    }
                    TdApi.ChatPhotos photos = photoResult.get();
                    if (photos.totalCount == 0 || photos.photos.length == 0) {
                        sink.success(List.of());
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
                    sink.success(albumContent);
                })
        );
    }

    private void sendAlbum(long chatId, List<TdApi.InputMessageContent> content) {
        client.send(new TdApi.SendMessageAlbum(
                chatId, 0, null, null,
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
