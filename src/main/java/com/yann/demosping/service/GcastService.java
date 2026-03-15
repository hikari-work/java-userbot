package com.yann.demosping.service;

import com.yann.demosping.dto.GcastConfig;
import it.tdlight.jni.TdApi;
import it.tdlight.client.SimpleTelegramClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
@Service
public class GcastService {

    private final SimpleTelegramClient userBotClient;
    private final GcastStateService stateService;
    private final TaskScheduler taskScheduler;
    private final GcastMessageCache messageCache;

    @Value("${bot.token}")
    private String botToken;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public GcastService(
            @Qualifier("userBotClient") SimpleTelegramClient userBotClient,
            GcastStateService stateService,
            TaskScheduler taskScheduler,
            GcastMessageCache messageCache) {
        this.userBotClient = userBotClient;
        this.stateService = stateService;
        this.taskScheduler = taskScheduler;
        this.messageCache = messageCache;
    }

    private CompletableFuture<long[]> fetchChatList(TdApi.ChatList chatList) {
        CompletableFuture<long[]> future = new CompletableFuture<>();
        TdApi.GetChats getChats = new TdApi.GetChats();
        getChats.chatList = chatList;
        getChats.limit = 2000;
        userBotClient.send(getChats, result -> {
            if (result.isError()) {
                log.warn("Failed to fetch chat list: {}", result.getError().message);
                future.complete(new long[0]);
            } else {
                future.complete(result.get().chatIds);
            }
        });
        return future;
    }

    public CompletableFuture<List<Long>> resolveChatIds(GcastConfig config) {
        Set<String> modes = config.filterModes == null ? new HashSet<>() : config.filterModes;

        boolean useMainChatList = modes.contains("mainChatList");
        boolean useArchived = modes.contains("archived");
        boolean useFolder = modes.contains("folder");
        boolean useWhitelist = modes.contains("whitelist");
        boolean useLabel = modes.contains("label");
        boolean useBlacklist = modes.contains("blacklist");
        boolean filterSupergroup = modes.contains("supergroup");
        boolean filterBasicGroup = modes.contains("basicGroup");
        boolean filterChannel = modes.contains("channel");
        boolean filterPrivateChat = modes.contains("privateChat");

        boolean noSourceSelected = !useMainChatList && !useArchived && !useFolder && !useWhitelist && !useLabel;
        if (noSourceSelected) {
            useMainChatList = true;
            useArchived = true;
        }

        List<CompletableFuture<long[]>> sourceFutures = new ArrayList<>();

        if (useMainChatList) {
            sourceFutures.add(fetchChatList(new TdApi.ChatListMain()));
        }
        if (useArchived) {
            sourceFutures.add(fetchChatList(new TdApi.ChatListArchive()));
        }
        if (useFolder && config.folderId != 0) {
            sourceFutures.add(fetchChatList(new TdApi.ChatListFolder(config.folderId)));
        }

        final boolean finalUseWhitelist = useWhitelist;
        final boolean finalUseLabel = useLabel;
        final boolean finalUseBlacklist = useBlacklist;
        final boolean finalFilterSupergroup = filterSupergroup;
        final boolean finalFilterBasicGroup = filterBasicGroup;
        final boolean finalFilterChannel = filterChannel;
        final boolean finalFilterPrivateChat = filterPrivateChat;

        return CompletableFuture.allOf(sourceFutures.toArray(new CompletableFuture[0]))
                .thenCompose(ignored -> {
                    Set<Long> sourceIds = new LinkedHashSet<>();
                    for (CompletableFuture<long[]> f : sourceFutures) {
                        try {
                            for (long id : f.get()) {
                                sourceIds.add(id);
                            }
                        } catch (Exception e) {
                            log.warn("Error collecting source chat ids", e);
                        }
                    }

                    if (finalUseWhitelist) {
                        sourceIds.addAll(stateService.getWhitelist());
                    }
                    if (finalUseLabel && config.labelName != null && !config.labelName.isBlank()) {
                        sourceIds.addAll(stateService.getLabel(config.labelName));
                    }

                    Set<Long> blacklistIds = finalUseBlacklist ? stateService.getBlacklist() : new HashSet<>();

                    boolean hasTypeFilter = finalFilterSupergroup || finalFilterBasicGroup || finalFilterChannel || finalFilterPrivateChat;

                    if (!hasTypeFilter && !finalUseBlacklist) {
                        List<Long> result = new ArrayList<>(sourceIds);
                        Set<Long> alreadySent = new HashSet<>(config.sentChatIds != null ? config.sentChatIds : List.of());
                        result.removeIf(alreadySent::contains);
                        return CompletableFuture.completedFuture(result);
                    }

                    if (!hasTypeFilter) {
                        List<Long> result = new ArrayList<>(sourceIds);
                        result.removeIf(blacklistIds::contains);
                        Set<Long> alreadySent = new HashSet<>(config.sentChatIds != null ? config.sentChatIds : List.of());
                        result.removeIf(alreadySent::contains);
                        return CompletableFuture.completedFuture(result);
                    }

                    List<CompletableFuture<Long>> typedFutures = new ArrayList<>();
                    for (Long chatId : sourceIds) {
                        if (blacklistIds.contains(chatId)) continue;
                        CompletableFuture<Long> typeFuture = new CompletableFuture<>();
                        TdApi.GetChat getChat = new TdApi.GetChat();
                        getChat.chatId = chatId;
                        userBotClient.send(getChat, result -> {
                            if (result.isError()) {
                                typeFuture.complete(null);
                                return;
                            }
                            TdApi.Chat chat = result.get();
                            boolean matches = false;
                            if (finalFilterSupergroup && chat.type instanceof TdApi.ChatTypeSupergroup s && !s.isChannel) {
                                matches = true;
                            }
                            if (finalFilterChannel && chat.type instanceof TdApi.ChatTypeSupergroup s && s.isChannel) {
                                matches = true;
                            }
                            if (finalFilterBasicGroup && chat.type instanceof TdApi.ChatTypeBasicGroup) {
                                matches = true;
                            }
                            if (finalFilterPrivateChat && chat.type instanceof TdApi.ChatTypePrivate) {
                                matches = true;
                            }
                            typeFuture.complete(matches ? chatId : null);
                        });
                        typedFutures.add(typeFuture);
                    }

                    return CompletableFuture.allOf(typedFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> {
                                Set<Long> alreadySent = new HashSet<>(config.sentChatIds != null ? config.sentChatIds : List.of());
                                List<Long> result = new ArrayList<>();
                                for (CompletableFuture<Long> f : typedFutures) {
                                    try {
                                        Long id = f.get();
                                        if (id != null && !alreadySent.contains(id)) {
                                            result.add(id);
                                        }
                                    } catch (Exception e) {
                                        log.warn("Error resolving chat type", e);
                                    }
                                }
                                return result;
                            });
                });
    }

    private long botUserId() {
        return Long.parseLong(botToken.split(":")[0]);
    }

    private CompletableFuture<TdApi.InputMessageContent> fetchMessageContent(long chatId, long messageId) {
        CompletableFuture<TdApi.InputMessageContent> future = new CompletableFuture<>();
        TdApi.GetMessage req = new TdApi.GetMessage();
        req.chatId = chatId;
        req.messageId = messageId;
        userBotClient.send(req, result -> {
            if (result.isError()) {
                future.completeExceptionally(new RuntimeException(result.getError().message));
                return;
            }
            TdApi.InputMessageContent content = convertContent(result.get().content);
            future.complete(content);
        });
        return future;
    }

    private TdApi.InputMessageContent convertContent(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageText mt) {
            TdApi.InputMessageText t = new TdApi.InputMessageText();
            t.text = mt.text;
            return t;
        }
        if (content instanceof TdApi.MessagePhoto mp && mp.photo.sizes.length > 0) {
            TdApi.InputMessagePhoto p = new TdApi.InputMessagePhoto();
            p.photo = new TdApi.InputFileRemote(mp.photo.sizes[mp.photo.sizes.length - 1].photo.remote.id);
            p.caption = mp.caption;
            return p;
        }
        if (content instanceof TdApi.MessageVideo mv) {
            TdApi.InputMessageVideo v = new TdApi.InputMessageVideo();
            v.video = new TdApi.InputFileRemote(mv.video.video.remote.id);
            v.caption = mv.caption;
            return v;
        }
        if (content instanceof TdApi.MessageDocument md) {
            TdApi.InputMessageDocument d = new TdApi.InputMessageDocument();
            d.document = new TdApi.InputFileRemote(md.document.document.remote.id);
            d.caption = md.caption;
            return d;
        }
        if (content instanceof TdApi.MessageAudio ma) {
            TdApi.InputMessageAudio a = new TdApi.InputMessageAudio();
            a.audio = new TdApi.InputFileRemote(ma.audio.audio.remote.id);
            a.caption = ma.caption;
            return a;
        }
        if (content instanceof TdApi.MessageAnimation man) {
            TdApi.InputMessageAnimation an = new TdApi.InputMessageAnimation();
            an.animation = new TdApi.InputFileRemote(man.animation.animation.remote.id);
            an.caption = man.caption;
            return an;
        }
        if (content instanceof TdApi.MessageSticker ms) {
            TdApi.InputMessageSticker s = new TdApi.InputMessageSticker();
            s.sticker = new TdApi.InputFileRemote(ms.sticker.sticker.remote.id);
            return s;
        }
        return null; // unsupported type — caller will fall back to ForwardMessages
    }

    private CompletableFuture<Void> forwardMessageViaBot(String sessionId, long targetChatId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        TdApi.GetInlineQueryResults req = new TdApi.GetInlineQueryResults();
        req.botUserId = botUserId();
        req.chatId = targetChatId;
        req.query = "gcast " + sessionId;
        req.offset = "";
        userBotClient.send(req, result -> {
            if (result.isError()) {
                log.warn("GetInlineQueryResults failed for sid={}: {}", sessionId, result.getError().message);
                future.complete(null);
                return;
            }
            TdApi.InlineQueryResults results = result.get();
            if (results.results.length == 0) {
                log.warn("No inline results for sid={}", sessionId);
                future.complete(null);
                return;
            }
            TdApi.SendInlineQueryResultMessage send = new TdApi.SendInlineQueryResultMessage();
            send.chatId = targetChatId;
            send.queryId = results.inlineQueryId;
            send.resultId = extractInlineResultId(results.results[0]);
            send.hideViaBot = false;
            userBotClient.send(send, sendResult -> {
                if (sendResult.isError()) {
                    log.warn("SendInlineQueryResultMessage failed to chatId={}: {}", targetChatId, sendResult.getError().message);
                }
                future.complete(null);
            });
        });
        return future;
    }

    private CompletableFuture<Void> forwardMessage(long sourceChatId, long sourceMessageId, long targetChatId, boolean sendCopy) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        TdApi.ForwardMessages fwd = new TdApi.ForwardMessages();
        fwd.chatId = targetChatId;
        fwd.fromChatId = sourceChatId;
        fwd.messageIds = new long[]{sourceMessageId};
        fwd.options = new TdApi.MessageSendOptions();
        fwd.sendCopy = sendCopy;
        userBotClient.send(fwd, result -> {
            if (result.isError()) {
                log.warn("Failed to forward message to chatId={}: {}", targetChatId, result.getError().message);
            }
            future.complete(null);
        });
        return future;
    }

    public void executeBroadcast(String sessionId, List<Long> chatIds, GcastConfig config, Consumer<String> progressCallback) {
        stateService.addRunningSession(sessionId);
        CompletableFuture.runAsync(() -> {
            // If sendViaBot mode, pre-cache message content if not already cached
            if (config.sendViaBot && messageCache.get(sessionId) == null) {
                try {
                    TdApi.InputMessageContent content = fetchMessageContent(config.sourceChatId, config.sourceMessageId).get(15, TimeUnit.SECONDS);
                    if (content != null) {
                        messageCache.put(sessionId, content);
                    }
                } catch (Exception e) {
                    log.warn("Failed to pre-cache message content for sid={}: {}", sessionId, e.getMessage());
                }
            }

            int total = chatIds.size();
            int sent = 0;

            for (Long targetChatId : chatIds) {
                if (stateService.isCancelled(sessionId)) {
                    progressCallback.accept("CANCELLED");
                    stateService.clearCancelFlag(sessionId);
                    if (config.sendViaBot) messageCache.remove(sessionId);
                    return;
                }

                try {
                    if (config.sendViaBot) {
                        forwardMessageViaBot(sessionId, targetChatId).get(30, TimeUnit.SECONDS);
                    } else {
                        forwardMessage(config.sourceChatId, config.sourceMessageId, targetChatId, config.noForwardHeader).get(30, TimeUnit.SECONDS);
                    }
                    sent++;

                    GcastConfig latest = stateService.getSession(sessionId);
                    if (latest != null) {
                        if (latest.sentChatIds == null) latest.sentChatIds = new ArrayList<>();
                        latest.sentChatIds.add(targetChatId);
                        if (sent % 5 == 0) {
                            stateService.saveSession(sessionId, latest);
                        }
                    }

                    progressCallback.accept(sent + "/" + total);

                    if (config.delayMs > 0) {
                        Thread.sleep(config.delayMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    progressCallback.accept("CANCELLED");
                    return;
                } catch (Exception e) {
                    log.warn("Error during broadcast to chatId={}: {}", targetChatId, e.getMessage());
                }
            }

            GcastConfig latest = stateService.getSession(sessionId);
            if (latest != null) {
                stateService.saveSession(sessionId, latest);
            }

            if (config.sendViaBot) messageCache.remove(sessionId);
            progressCallback.accept("DONE:" + sent + "/" + total);
        });
    }

    private String extractInlineResultId(TdApi.InlineQueryResult result) {
        if (result instanceof TdApi.InlineQueryResultArticle r) return r.id;
        if (result instanceof TdApi.InlineQueryResultAnimation r) return r.id;
        if (result instanceof TdApi.InlineQueryResultAudio r) return r.id;
        if (result instanceof TdApi.InlineQueryResultContact r) return r.id;
        if (result instanceof TdApi.InlineQueryResultDocument r) return r.id;
        if (result instanceof TdApi.InlineQueryResultPhoto r) return r.id;
        if (result instanceof TdApi.InlineQueryResultSticker r) return r.id;
        if (result instanceof TdApi.InlineQueryResultVenue r) return r.id;
        if (result instanceof TdApi.InlineQueryResultVideo r) return r.id;
        if (result instanceof TdApi.InlineQueryResultVoiceNote r) return r.id;
        return "";
    }

    public void cancelBroadcast(String sessionId) {
        stateService.setCancelFlag(sessionId);
        ScheduledFuture<?> task = scheduledTasks.get(sessionId);
        if (task != null) {
            task.cancel(false);
            scheduledTasks.remove(sessionId);
        }
    }

    public void scheduleRecurring(String sessionId, GcastConfig config, Consumer<String> progressCallback) {
        long intervalMs = config.intervalMs > 0 ? config.intervalMs : 60_000;
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() -> {
            GcastConfig latest = stateService.getSession(sessionId);
            if (latest == null) {
                log.warn("Session {} not found during scheduled run, skipping", sessionId);
                return;
            }
            if (stateService.isCancelled(sessionId)) return;

            latest.sentChatIds = new ArrayList<>();
            stateService.saveSession(sessionId, latest);

            resolveChatIds(latest).thenAccept(chatIds -> {
                latest.totalChats = chatIds.size();
                stateService.saveSession(sessionId, latest);
                executeBroadcast(sessionId, chatIds, latest, progressCallback);
            });
        }, java.time.Duration.ofMillis(intervalMs));
        scheduledTasks.put(sessionId, task);
    }
}
