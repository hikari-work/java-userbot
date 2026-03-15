package com.yann.demosping.service;

import com.yann.demosping.dto.GcastConfig;
import it.tdlight.jni.TdApi;
import it.tdlight.client.SimpleTelegramClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
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

    private Mono<long[]> fetchChatList(TdApi.ChatList chatList) {
        return Mono.create(sink -> {
            TdApi.GetChats getChats = new TdApi.GetChats();
            getChats.chatList = chatList;
            getChats.limit = 2000;
            userBotClient.send(getChats, result -> {
                if (result.isError()) {
                    log.warn("Failed to fetch chat list: {}", result.getError().message);
                    sink.success(new long[0]);
                } else {
                    sink.success(result.get().chatIds);
                }
            });
        });
    }

    public Mono<List<Long>> resolveChatIds(GcastConfig config) {
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

        List<Mono<long[]>> sourceMonos = new ArrayList<>();
        if (useMainChatList) sourceMonos.add(fetchChatList(new TdApi.ChatListMain()));
        if (useArchived) sourceMonos.add(fetchChatList(new TdApi.ChatListArchive()));
        if (useFolder && config.folderId != 0) sourceMonos.add(fetchChatList(new TdApi.ChatListFolder(config.folderId)));

        final boolean finalUseWhitelist = useWhitelist;
        final boolean finalUseLabel = useLabel;
        final boolean finalUseBlacklist = useBlacklist;
        final boolean finalFilterSupergroup = filterSupergroup;
        final boolean finalFilterBasicGroup = filterBasicGroup;
        final boolean finalFilterChannel = filterChannel;
        final boolean finalFilterPrivateChat = filterPrivateChat;

        Mono<Set<Long>> sourceIdsMono = sourceMonos.isEmpty()
                ? Mono.just(new LinkedHashSet<>())
                : Flux.merge(sourceMonos)
                        .reduce(new LinkedHashSet<Long>(), (set, arr) -> {
                            for (long id : arr) set.add(id);
                            return set;
                        });

        Mono<Set<Long>> whitelistMono = finalUseWhitelist
                ? stateService.getWhitelist()
                : Mono.just(new HashSet<>());
        Mono<Set<Long>> labelMono = (finalUseLabel && config.labelName != null && !config.labelName.isBlank())
                ? stateService.getLabel(config.labelName)
                : Mono.just(new HashSet<>());
        Mono<Set<Long>> blacklistMono = finalUseBlacklist
                ? stateService.getBlacklist()
                : Mono.just(new HashSet<>());

        return sourceIdsMono.zipWith(Mono.zip(whitelistMono, labelMono, blacklistMono))
                .flatMap(t -> {
                    Set<Long> sourceIds = t.getT1();
                    sourceIds.addAll(t.getT2().getT1()); // whitelist
                    sourceIds.addAll(t.getT2().getT2()); // label
                    Set<Long> blacklistIds = t.getT2().getT3();

                    boolean hasTypeFilter = finalFilterSupergroup || finalFilterBasicGroup || finalFilterChannel || finalFilterPrivateChat;
                    Set<Long> alreadySent = new HashSet<>(config.sentChatIds != null ? config.sentChatIds : List.of());

                    if (!hasTypeFilter && !finalUseBlacklist) {
                        List<Long> result = new ArrayList<>(sourceIds);
                        result.removeIf(alreadySent::contains);
                        return Mono.just(result);
                    }

                    if (!hasTypeFilter) {
                        List<Long> result = new ArrayList<>(sourceIds);
                        result.removeIf(blacklistIds::contains);
                        result.removeIf(alreadySent::contains);
                        return Mono.just(result);
                    }

                    List<Mono<Long>> typedMonos = new ArrayList<>();
                    for (Long chatId : sourceIds) {
                        if (blacklistIds.contains(chatId)) continue;
                        final long finalChatId = chatId;
                        Mono<Long> typeMono = Mono.<Long>create(sink -> {
                            TdApi.GetChat getChat = new TdApi.GetChat();
                            getChat.chatId = finalChatId;
                            userBotClient.send(getChat, result -> {
                                if (result.isError()) { sink.success(null); return; }
                                TdApi.Chat chat = result.get();
                                boolean matches = false;
                                if (finalFilterSupergroup && chat.type instanceof TdApi.ChatTypeSupergroup s && !s.isChannel) matches = true;
                                if (finalFilterChannel && chat.type instanceof TdApi.ChatTypeSupergroup s && s.isChannel) matches = true;
                                if (finalFilterBasicGroup && chat.type instanceof TdApi.ChatTypeBasicGroup) matches = true;
                                if (finalFilterPrivateChat && chat.type instanceof TdApi.ChatTypePrivate) matches = true;
                                sink.success(matches ? finalChatId : null);
                            });
                        });
                        typedMonos.add(typeMono);
                    }

                    return Flux.merge(typedMonos)
                            .filter(id -> id != null && !alreadySent.contains(id))
                            .collectList();
                });
    }

    private long botUserId() {
        return Long.parseLong(botToken.split(":")[0]);
    }

    private Mono<TdApi.InputMessageContent> fetchMessageContent(long chatId, long messageId) {
        return Mono.create(sink -> {
            TdApi.GetMessage req = new TdApi.GetMessage();
            req.chatId = chatId;
            req.messageId = messageId;
            userBotClient.send(req, result -> {
                if (result.isError()) {
                    sink.error(new RuntimeException(result.getError().message));
                    return;
                }
                sink.success(convertContent(result.get().content));
            });
        });
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
        return null;
    }

    private Mono<Void> forwardMessageViaBot(String sessionId, long targetChatId) {
        return Mono.create(sink -> {
            TdApi.GetInlineQueryResults req = new TdApi.GetInlineQueryResults();
            req.botUserId = botUserId();
            req.chatId = targetChatId;
            req.query = "gcast " + sessionId;
            req.offset = "";
            userBotClient.send(req, result -> {
                if (result.isError()) {
                    log.warn("GetInlineQueryResults failed for sid={}: {}", sessionId, result.getError().message);
                    sink.success();
                    return;
                }
                TdApi.InlineQueryResults results = result.get();
                if (results.results.length == 0) {
                    log.warn("No inline results for sid={}", sessionId);
                    sink.success();
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
                    sink.success();
                });
            });
        });
    }

    private Mono<Void> forwardMessage(long sourceChatId, long sourceMessageId, long targetChatId, boolean sendCopy) {
        return Mono.create(sink -> {
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
                sink.success();
            });
        });
    }

    public void executeBroadcast(String sessionId, List<Long> chatIds, GcastConfig config, Consumer<String> progressCallback) {
        stateService.addRunningSession(sessionId).subscribe();
        int total = chatIds.size();
        AtomicInteger sentRef = new AtomicInteger(0);

        Mono<Void> preCacheMono = (config.sendViaBot && messageCache.get(sessionId) == null)
                ? fetchMessageContent(config.sourceChatId, config.sourceMessageId)
                        .doOnNext(content -> { if (content != null) messageCache.put(sessionId, content); })
                        .then()
                        .onErrorResume(e -> {
                            log.warn("Failed to pre-cache message for sid={}: {}", sessionId, e.getMessage());
                            return Mono.empty();
                        })
                : Mono.empty();

        preCacheMono
                .thenMany(Flux.fromIterable(chatIds))
                .concatMap(targetChatId ->
                        stateService.isCancelled(sessionId)
                                .flatMap(cancelled -> {
                                    if (cancelled) return Mono.error(new BroadcastCancelledException());

                                    Mono<Void> sendMono = config.sendViaBot
                                            ? forwardMessageViaBot(sessionId, targetChatId)
                                            : forwardMessage(config.sourceChatId, config.sourceMessageId, targetChatId, config.noForwardHeader);

                                    return sendMono
                                            .then(Mono.fromRunnable(() -> progressCallback.accept(sentRef.incrementAndGet() + "/" + total)))
                                            .then(stateService.getSession(sessionId)
                                                    .flatMap(latest -> {
                                                        if (latest.sentChatIds == null) latest.sentChatIds = new ArrayList<>();
                                                        latest.sentChatIds.add(targetChatId);
                                                        if (sentRef.get() % 5 == 0)
                                                            return stateService.saveSession(sessionId, latest).then();
                                                        return Mono.<Void>empty();
                                                    })
                                            )
                                            .then(config.delayMs > 0
                                                    ? Mono.delay(Duration.ofMillis(config.delayMs)).then()
                                                    : Mono.<Void>empty())
                                            .onErrorResume(ex -> {
                                                log.warn("Error during broadcast to chatId={}: {}", targetChatId, ex.getMessage());
                                                return Mono.empty();
                                            });
                                })
                )
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        v -> {},
                        ex -> {
                            if (ex instanceof BroadcastCancelledException) {
                                progressCallback.accept("CANCELLED");
                                stateService.clearCancelFlag(sessionId).subscribe();
                            } else {
                                log.error("Broadcast failed for session {}", sessionId, ex);
                            }
                            if (config.sendViaBot) messageCache.remove(sessionId);
                            stateService.removeRunningSession(sessionId).subscribe();
                        },
                        () -> {
                            if (config.sendViaBot) messageCache.remove(sessionId);
                            stateService.removeRunningSession(sessionId).subscribe();
                            stateService.getSession(sessionId)
                                    .subscribe(latest -> {
                                        if (latest != null) stateService.saveSession(sessionId, latest).subscribe();
                                    });
                            progressCallback.accept("DONE:" + sentRef.get() + "/" + total);
                        }
                );
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
        stateService.setCancelFlag(sessionId).subscribe();
        ScheduledFuture<?> task = scheduledTasks.get(sessionId);
        if (task != null) {
            task.cancel(false);
            scheduledTasks.remove(sessionId);
        }
    }

    public void scheduleRecurring(String sessionId, GcastConfig config, Consumer<String> progressCallback) {
        long intervalMs = config.intervalMs > 0 ? config.intervalMs : 60_000;
        ScheduledFuture<?> task = taskScheduler.scheduleAtFixedRate(() ->
                stateService.isCancelled(sessionId)
                        .filter(cancelled -> !cancelled)
                        .flatMap(__ -> stateService.getSession(sessionId))
                        .subscribe(latest -> {
                            if (latest == null) {
                                log.warn("Session {} not found during scheduled run, skipping", sessionId);
                                return;
                            }
                            latest.sentChatIds = new ArrayList<>();
                            stateService.saveSession(sessionId, latest).subscribe();
                            resolveChatIds(latest).subscribe(
                                    chatIds -> {
                                        latest.totalChats = chatIds.size();
                                        stateService.saveSession(sessionId, latest).subscribe();
                                        executeBroadcast(sessionId, chatIds, latest, progressCallback);
                                    },
                                    ex -> log.error("resolveChatIds failed for session {}: {}", sessionId, ex.getMessage(), ex)
                            );
                        }, ex -> log.error("scheduleRecurring error for session {}: {}", sessionId, ex.getMessage(), ex)),
                Duration.ofMillis(intervalMs));
        scheduledTasks.put(sessionId, task);
    }

    private static final class BroadcastCancelledException extends RuntimeException {
        BroadcastCancelledException() {
            super("Broadcast cancelled", null, true, false);
        }
    }
}
