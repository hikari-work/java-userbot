package com.yann.demosping.bot.inline;

import com.yann.demosping.dto.GcastConfig;
import com.yann.demosping.service.ChatFolderCache;
import com.yann.demosping.service.GcastService;
import com.yann.demosping.service.GcastStateService;
import it.tdlight.jni.TdApi;
import it.tdlight.client.SimpleTelegramClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class GcastCallbackHandler {

    private final ApplicationContext applicationContext;
    private final GcastStateService stateService;
    private final GcastService gcastService;
    private final ChatFolderCache chatFolderCache;

    public GcastCallbackHandler(
            ApplicationContext applicationContext,
            @Qualifier("userBotClient") SimpleTelegramClient userBotClient,
            GcastStateService stateService,
            GcastService gcastService,
            ChatFolderCache chatFolderCache) {
        this.applicationContext = applicationContext;
        this.stateService = stateService;
        this.gcastService = gcastService;
        this.chatFolderCache = chatFolderCache;
    }

    private SimpleTelegramClient botClient() {
        return applicationContext.getBean("botClient", SimpleTelegramClient.class);
    }

    private void answer(long queryId, String text) {
        TdApi.AnswerCallbackQuery req = new TdApi.AnswerCallbackQuery(queryId, text, false, "", 0);
        botClient().send(req, r -> {});
    }

    public void editConfig(long chatId, long messageId, String html, TdApi.ReplyMarkupInlineKeyboard keyboard) {
        botClient().send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText ft;
            if (parseResult.isError()) {
                ft = new TdApi.FormattedText(html, new TdApi.TextEntity[0]);
            } else {
                ft = parseResult.get();
            }
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = ft;
            TdApi.EditMessageText editReq = new TdApi.EditMessageText();
            editReq.chatId = chatId;
            editReq.messageId = messageId;
            editReq.inputMessageContent = content;
            editReq.replyMarkup = keyboard;
            botClient().send(editReq, r -> {
                if (r.isError()) {
                    log.warn("Failed to edit config message chatId={} messageId={}: {}",
                            chatId, messageId, r.getError().message);
                }
            });
        });
    }

    public void editConfigInline(String inlineMessageId, String html, TdApi.ReplyMarkupInlineKeyboard keyboard) {
        botClient().send(new TdApi.ParseTextEntities(html, new TdApi.TextParseModeHTML()), parseResult -> {
            TdApi.FormattedText ft = parseResult.isError()
                    ? new TdApi.FormattedText(html, new TdApi.TextEntity[0])
                    : parseResult.get();
            TdApi.InputMessageText content = new TdApi.InputMessageText();
            content.text = ft;
            botClient().send(new TdApi.EditInlineMessageText(inlineMessageId, keyboard, content), r -> {
                if (r.isError() && !r.getError().message.equals("MESSAGE_NOT_MODIFIED")) {
                    log.warn("EditInlineMessageText failed inlineMessageId={}: {}", inlineMessageId, r.getError().message);
                }
            });
        });
    }

    public void editConfig(GcastConfig config, String html, TdApi.ReplyMarkupInlineKeyboard keyboard) {
        if (config.controlInlineMessageId != null && !config.controlInlineMessageId.isBlank()) {
            editConfigInline(config.controlInlineMessageId, html, keyboard);
        } else {
            editConfig(config.controlChatId, config.controlMessageId, html, keyboard);
        }
    }

    private String formatDelay(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60000) return (ms / 1000) + "s";
        if (ms < 3600000) return (ms / 60000) + "m";
        if (ms < 86400000) return (ms / 3600000) + "j";
        return (ms / 86400000) + "h";
    }

    private String formatFilters(Set<String> modes) {
        if (modes == null || modes.isEmpty()) return "-";
        List<String> labels = new ArrayList<>();
        if (modes.contains("mainChatList")) labels.add("Main Chat");
        if (modes.contains("archived")) labels.add("Arsip");
        if (modes.contains("folder")) labels.add("Folder");
        if (modes.contains("whitelist")) labels.add("Whitelist");
        if (modes.contains("blacklist")) labels.add("Blacklist");
        if (modes.contains("label")) labels.add("Label");
        if (modes.contains("supergroup")) labels.add("Supergroup");
        if (modes.contains("basicGroup")) labels.add("BasicGroup");
        if (modes.contains("channel")) labels.add("Channel");
        if (modes.contains("privateChat")) labels.add("Private Chat");
        return String.join(", ", labels);
    }

    public TdApi.ReplyMarkupInlineKeyboard buildDelayKeyboard(String sid) {
        String p = "gc:d:" + sid + ":";
        TdApi.InlineKeyboardButton[][] rows = new TdApi.InlineKeyboardButton[][]{
                {btn("1s", p + 1000), btn("2s", p + 2000), btn("5s", p + 5000),
                        btn("10s", p + 10000), btn("20s", p + 20000), btn("30s", p + 30000)},
                {btn("1m", p + 60000), btn("2m", p + 120000), btn("5m", p + 300000),
                        btn("10m", p + 600000), btn("15m", p + 900000), btn("30m", p + 1800000)},
                {btn("60m", p + 3600000), btn("120m", p + 7200000), btn("150m", p + 9000000),
                        btn("240m", p + 14400000), btn("12h", p + 43200000), btn("24h", p + 86400000)},
                {btn("❌ Batal", "gc:ca:" + sid)}
        };
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    public TdApi.ReplyMarkupInlineKeyboard buildFilterKeyboard(String sid, Set<String> selected) {
        if (selected == null) selected = new HashSet<>();
        String check = "✅ ";
        String empty = "☐ ";

        boolean mainSel = selected.contains("mainChatList");
        boolean archSel = selected.contains("archived");
        boolean folderSel = selected.contains("folder");
        boolean wlSel = selected.contains("whitelist");
        boolean blSel = selected.contains("blacklist");
        boolean labelSel = selected.contains("label");
        boolean sgSel = selected.contains("supergroup");
        boolean bgSel = selected.contains("basicGroup");
        boolean chSel = selected.contains("channel");
        boolean pcSel = selected.contains("privateChat");

        String folderLabel = (folderSel ? check : empty) + "Folder ▶";
        String labelLabel = (labelSel ? check : empty) + "Label ▶";

        TdApi.InlineKeyboardButton[][] rows = new TdApi.InlineKeyboardButton[][]{
                {
                        btn((mainSel ? check : empty) + "Main Chat", "gc:f:" + sid + ":mainChatList"),
                        btn((archSel ? check : empty) + "Archived", "gc:f:" + sid + ":archived"),
                        btn(folderLabel, "gc:f:" + sid + ":folder")
                },
                {
                        btn((wlSel ? check : empty) + "Whitelist", "gc:f:" + sid + ":whitelist"),
                        btn((blSel ? check : empty) + "Blacklist", "gc:f:" + sid + ":blacklist"),
                        btn(labelLabel, "gc:f:" + sid + ":label")
                },
                {
                        btn((sgSel ? check : empty) + "Supergroup", "gc:f:" + sid + ":supergroup"),
                        btn((bgSel ? check : empty) + "BasicGroup", "gc:f:" + sid + ":basicGroup"),
                        btn((chSel ? check : empty) + "Channel", "gc:f:" + sid + ":channel")
                },
                {
                        btn((pcSel ? check : empty) + "Private Chat", "gc:f:" + sid + ":privateChat")
                },
                {
                        btn("✅ Selesai", "gc:fd:" + sid),
                        btn("❌ Batal", "gc:ca:" + sid)
                }
        };
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    private TdApi.ReplyMarkupInlineKeyboard buildScheduleKeyboard(String sid) {
        TdApi.InlineKeyboardButton[][] rows = new TdApi.InlineKeyboardButton[][]{
                {
                        btn("▶ Sekali", "gc:ro:" + sid),
                        btn("🕐 Terjadwal", "gc:rs:" + sid),
                        btn("❌ Batal", "gc:ca:" + sid)
                }
        };
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    public TdApi.ReplyMarkupInlineKeyboard buildSendModeKeyboard(String sid, GcastConfig config) {
        String fwdLabel  = (!config.sendViaBot && !config.noForwardHeader ? "✅ " : "") + "📨 Forward";
        String copyLabel = (!config.sendViaBot &&  config.noForwardHeader ? "✅ " : "") + "📋 Copy";
        String botLabel  = ( config.sendViaBot                            ? "✅ " : "") + "🤖 Via Bot";

        TdApi.InlineKeyboardButton[][] rows = new TdApi.InlineKeyboardButton[][]{
                {
                        btn(fwdLabel,  "gc:sm:" + sid + ":fwd"),
                        btn(copyLabel, "gc:sm:" + sid + ":copy"),
                        btn(botLabel,  "gc:sm:" + sid + ":bot")
                },
                {
                        btn("❌ Batal", "gc:ca:" + sid)
                }
        };
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    private TdApi.ReplyMarkupInlineKeyboard buildCancelKeyboard(String sid) {
        TdApi.InlineKeyboardButton[][] rows = new TdApi.InlineKeyboardButton[][]{
                {btn("❌ Batal", "gc:ca:" + sid)}
        };
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    public TdApi.ReplyMarkupInlineKeyboard buildStopKeyboard(String sid) {
        TdApi.InlineKeyboardButton[][] rows = new TdApi.InlineKeyboardButton[][]{
                {btn("⏹ Stop", "gc:stop:" + sid)}
        };
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    private TdApi.ReplyMarkupInlineKeyboard buildEmptyKeyboard() {
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = new TdApi.InlineKeyboardButton[0][0];
        return keyboard;
    }

    public TdApi.InlineKeyboardButton btn(String text, String callbackData) {
        TdApi.InlineKeyboardButton button = new TdApi.InlineKeyboardButton();
        button.text = text;
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = callbackData.getBytes();
        button.type = type;
        return button;
    }

    /** Extract session ID from payload (second field after "gc:X:"). */
    private String extractSid(String payload) {
        // payloads: gc:d:{sid}:{val}, gc:fd:{sid}, gc:fsub:{sid}, gc:f:{sid}:{mode}, etc.
        // sid is always right after the second ':'
        int firstColon = payload.indexOf(':');
        if (firstColon < 0) return null;
        int secondColon = payload.indexOf(':', firstColon + 1);
        if (secondColon < 0) return null;
        // For payloads with another value after sid (gc:d:, gc:f:, gc:fl:, gc:lb:), find the third colon
        // For payloads with sid only (gc:fd:, gc:fsub:, etc.), the rest IS the sid
        String afterPrefix = payload.substring(secondColon + 1);
        // Check if this is a "sid only" payload
        String prefix = payload.substring(0, secondColon + 1);
        if (prefix.equals("gc:fd:") || prefix.equals("gc:fsub:") || prefix.equals("gc:lsub:")
                || prefix.equals("gc:back:") || prefix.equals("gc:ro:") || prefix.equals("gc:rs:")
                || prefix.equals("gc:stop:") || prefix.equals("gc:ca:")) {
            return afterPrefix;
        }
        // Otherwise sid is before the next colon
        int thirdColon = afterPrefix.indexOf(':');
        if (thirdColon < 0) return afterPrefix;
        return afterPrefix.substring(0, thirdColon);
    }

    public void handle(TdApi.UpdateNewCallbackQuery query, String payload) {
        try {
            // Always sync controlChatId/controlMessageId from the actual callback message.
            // The ID stored from the bot's send result can be a pending local ID;
            // the callback's messageId is the definitive server-assigned ID.
            String sid = extractSid(payload);
            if (sid != null) {
                GcastConfig cfg = stateService.getSession(sid);
                if (cfg != null && cfg.controlMessageId != query.messageId) {
                    cfg.controlChatId = query.chatId;
                    cfg.controlMessageId = query.messageId;
                    stateService.saveSession(sid, cfg);
                }
            }

            dispatchPayload(query.id, payload);
        } catch (Exception e) {
            log.error("Error handling gcast callback payload='{}': {}", payload, e.getMessage(), e);
        }
    }

    public void handleInline(TdApi.UpdateNewInlineCallbackQuery callbackQuery, String payload) {
        try {
            // Sync inlineMessageId from callback into config
            String sid = extractSid(payload);
            if (sid != null) {
                GcastConfig cfg = stateService.getSession(sid);
                if (cfg != null) {
                    if (!callbackQuery.inlineMessageId.equals(cfg.controlInlineMessageId)) {
                        cfg.controlInlineMessageId = callbackQuery.inlineMessageId;
                        stateService.saveSession(sid, cfg);
                    }
                }
            }

            dispatchPayload(callbackQuery.id, payload);
        } catch (Exception e) {
            log.error("Error handling gcast inline callback payload='{}': {}", payload, e.getMessage(), e);
        }
    }

    private void dispatchPayload(long callbackQueryId, String payload) {
        if (payload.startsWith("gc:d:")) {
            handleDelay(callbackQueryId, payload);
        } else if (payload.startsWith("gc:ri:")) {
            handleRunInterval(callbackQueryId, payload);
        } else if (payload.startsWith("gc:fd:")) {
            handleFilterDone(callbackQueryId, payload);
        } else if (payload.startsWith("gc:sm:")) {
            handleSendMode(callbackQueryId, payload);
        } else if (payload.startsWith("gc:fsub:")) {
            handleFolderSubmenu(callbackQueryId, payload);
        } else if (payload.startsWith("gc:lsub:")) {
            handleLabelSubmenu(callbackQueryId, payload);
        } else if (payload.startsWith("gc:fl:")) {
            handleFolderSelect(callbackQueryId, payload);
        } else if (payload.startsWith("gc:lb:")) {
            handleLabelSelect(callbackQueryId, payload);
        } else if (payload.startsWith("gc:back:")) {
            handleBackToFilter(callbackQueryId, payload);
        } else if (payload.startsWith("gc:f:")) {
            handleFilterToggle(callbackQueryId, payload);
        } else if (payload.startsWith("gc:ro:")) {
            handleRunOnce(callbackQueryId, payload);
        } else if (payload.startsWith("gc:rs:")) {
            handleRunScheduled(callbackQueryId, payload);
        } else if (payload.startsWith("gc:stop:")) {
            handleStop(callbackQueryId, payload);
        } else if (payload.startsWith("gc:ca:")) {
            handleCancel(callbackQueryId, payload);
        } else {
            log.warn("Unknown gcast callback payload: {}", payload);
        }
    }

    private void handleDelay(long callbackQueryId, String payload) {
        // gc:d:{sid}:{ms}
        String rest = payload.substring("gc:d:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        long delayMs;
        try {
            delayMs = Long.parseLong(rest.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            answer(callbackQueryId, "Invalid delay value");
            return;
        }

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        config.delayMs = delayMs;
        config.step = "FILTER";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Jeda dipilih!");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(delayMs) +
                "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
        editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
    }

    private void handleFilterToggle(long callbackQueryId, String payload) {
        // gc:f:{sid}:{mode}
        String rest = payload.substring("gc:f:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        String mode = rest.substring(lastColon + 1);

        if ("folder".equals(mode)) {
            handleFolderSubmenu(callbackQueryId, "gc:fsub:" + sid);
            return;
        }
        if ("label".equals(mode)) {
            handleLabelSubmenu(callbackQueryId, "gc:lsub:" + sid);
            return;
        }

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        if (config.filterModes == null) config.filterModes = new HashSet<>();
        if (config.filterModes.contains(mode)) {
            config.filterModes.remove(mode);
        } else {
            config.filterModes.add(mode);
        }
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Filter diperbarui!");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
        editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
    }

    private void handleFilterDone(long callbackQueryId, String payload) {
        // gc:fd:{sid}
        String sid = payload.substring("gc:fd:".length());

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        if (config.filterModes == null || config.filterModes.isEmpty()) {
            answer(callbackQueryId, "Pilih setidaknya 1 filter!");
            return;
        }

        config.step = "SEND_MODE";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Filter dikonfirmasi!");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                " | Filter: " + formatFilters(config.filterModes) +
                "\n\nLangkah 3: Pilih mode pengiriman";
        editConfig(config, html, buildSendModeKeyboard(sid, config));
    }

    private void handleSendMode(long callbackQueryId, String payload) {
        // gc:sm:{sid}:fwd|copy|bot
        String rest = payload.substring("gc:sm:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        String mode = rest.substring(lastColon + 1);

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        switch (mode) {
            case "fwd"  -> { config.sendViaBot = false; config.noForwardHeader = false; }
            case "copy" -> { config.sendViaBot = false; config.noForwardHeader = true;  }
            case "bot"  -> { config.sendViaBot = true;  config.noForwardHeader = false; }
            default -> {
                answer(callbackQueryId, "Mode tidak dikenal.");
                return;
            }
        }

        config.step = "SCHEDULE";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Mode pengiriman dipilih!");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                " | Filter: " + formatFilters(config.filterModes) +
                "\n\nLangkah 4: Jadwal pengiriman";
        editConfig(config, html, buildScheduleKeyboard(sid));
    }

    private void handleFolderSubmenu(long callbackQueryId, String payload) {
        // gc:fsub:{sid}
        String sid = payload.substring("gc:fsub:".length());

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        Map<Integer, String> folders = chatFolderCache.getFolders();
        if (folders.isEmpty()) {
            answer(callbackQueryId, "Belum ada folder. Buat folder di Telegram terlebih dahulu.");
            return;
        }

        config.step = "FILTER_FOLDER";
        stateService.saveSession(sid, config);

        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : folders.entrySet()) {
            String cb = "gc:fl:" + sid + ":" + entry.getKey();
            rows.add(new TdApi.InlineKeyboardButton[]{btn(entry.getValue(), cb)});
        }
        rows.add(new TdApi.InlineKeyboardButton[]{btn("◀ Kembali", "gc:back:" + sid)});

        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows.toArray(new TdApi.InlineKeyboardButton[0][]);

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                "\n\nPilih folder:";
        editConfig(config, html, keyboard);
        answer(callbackQueryId, "");
    }

    private void handleLabelSubmenu(long callbackQueryId, String payload) {
        // gc:lsub:{sid}
        String sid = payload.substring("gc:lsub:".length());

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        Set<String> labelNames = stateService.getLabelNames();
        if (labelNames == null || labelNames.isEmpty()) {
            answer(callbackQueryId, "Belum ada label. Gunakan ,addlabel <nama>");
            return;
        }

        config.step = "FILTER_LABEL";
        stateService.saveSession(sid, config);

        List<TdApi.InlineKeyboardButton[]> rows = new ArrayList<>();
        for (String name : labelNames) {
            String cb = "gc:lb:" + sid + ":" + name;
            rows.add(new TdApi.InlineKeyboardButton[]{btn(name, cb)});
        }
        rows.add(new TdApi.InlineKeyboardButton[]{btn("◀ Kembali", "gc:back:" + sid)});

        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows.toArray(new TdApi.InlineKeyboardButton[0][]);

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                "\n\nPilih label:";
        editConfig(config, html, keyboard);
        answer(callbackQueryId, "");
    }

    private void handleFolderSelect(long callbackQueryId, String payload) {
        // gc:fl:{sid}:{folderId}
        String rest = payload.substring("gc:fl:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        int folderId;
        try {
            folderId = Integer.parseInt(rest.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            answer(callbackQueryId, "Invalid folder id");
            return;
        }

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        if (config.filterModes == null) config.filterModes = new HashSet<>();
        config.filterModes.add("folder");
        config.folderId = folderId;
        config.folderName = chatFolderCache.getFolders().getOrDefault(folderId, "Folder " + folderId);
        config.step = "FILTER";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Folder dipilih!");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
        editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
    }

    private void handleLabelSelect(long callbackQueryId, String payload) {
        // gc:lb:{sid}:{labelName}
        String rest = payload.substring("gc:lb:".length());
        int firstColon = rest.indexOf(':');
        if (firstColon < 0) return;
        String sid = rest.substring(0, firstColon);
        String labelName = rest.substring(firstColon + 1);

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        if (config.filterModes == null) config.filterModes = new HashSet<>();
        config.filterModes.add("label");
        config.labelName = labelName;
        config.step = "FILTER";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Label dipilih!");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
        editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
    }

    private void handleBackToFilter(long callbackQueryId, String payload) {
        // gc:back:{sid}
        String sid = payload.substring("gc:back:".length());

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        config.step = "FILTER";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                "\n\nLangkah 2: Pilih target (bisa pilih beberapa)";
        editConfig(config, html, buildFilterKeyboard(sid, config.filterModes));
    }

    private void handleRunOnce(long callbackQueryId, String payload) {
        // gc:ro:{sid}
        String sid = payload.substring("gc:ro:".length());

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        config.scheduled = false;
        config.step = "RUNNING";
        config.status = "running";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Memulai broadcast...");

        String startHtml = "📢 <b>Broadcast berjalan</b>\nSession: " + sid +
                "\nJeda: " + formatDelay(config.delayMs) +
                " | Filter: " + formatFilters(config.filterModes) +
                "\nMemulai broadcast...";
        editConfig(config, startHtml, buildStopKeyboard(sid));

        final GcastConfig finalConfig = config;
        final String finalSid = sid;

        gcastService.resolveChatIds(finalConfig).thenAccept(chatIds -> {
            finalConfig.totalChats = chatIds.size();
            stateService.saveSession(finalSid, finalConfig);

            gcastService.executeBroadcast(finalSid, chatIds, finalConfig, progress -> {
                GcastConfig latest = stateService.getSession(finalSid);
                if (latest == null) return;

                if (progress.startsWith("DONE:")) {
                    String counts = progress.substring("DONE:".length());
                    String doneHtml = "✅ <b>Broadcast selesai!</b>\nSession: " + finalSid +
                            "\nTerkirim: " + counts;
                    editConfig(latest, doneHtml, buildEmptyKeyboard());
                    stateService.removeRunningSession(finalSid);
                } else if ("CANCELLED".equals(progress)) {
                    String cancelHtml = "⏹ <b>Broadcast dibatalkan</b>\nSession: " + finalSid;
                    editConfig(latest, cancelHtml, buildEmptyKeyboard());
                    stateService.removeRunningSession(finalSid);
                } else {
                    String progressHtml = "📢 <b>Broadcast berjalan</b>\nSession: " + finalSid +
                            "\nJeda: " + formatDelay(latest.delayMs) +
                            " | Filter: " + formatFilters(latest.filterModes) +
                            "\nProgress: " + progress + " chat";
                    editConfig(latest, progressHtml, buildStopKeyboard(finalSid));
                }
            });
        });
    }

    private TdApi.ReplyMarkupInlineKeyboard buildIntervalKeyboard(String sid) {
        String p = "gc:ri:" + sid + ":";
        TdApi.InlineKeyboardButton[][] rows = new TdApi.InlineKeyboardButton[][]{
                {btn("1s", p + 1000), btn("2s", p + 2000), btn("5s", p + 5000),
                        btn("10s", p + 10000), btn("20s", p + 20000), btn("30s", p + 30000)},
                {btn("1m", p + 60000), btn("2m", p + 120000), btn("5m", p + 300000),
                        btn("10m", p + 600000), btn("15m", p + 900000), btn("30m", p + 1800000)},
                {btn("60m", p + 3600000), btn("120m", p + 7200000), btn("150m", p + 9000000),
                        btn("240m", p + 14400000), btn("12h", p + 43200000), btn("24h", p + 86400000)},
                {btn("❌ Batal", "gc:ca:" + sid)}
        };
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    private void handleRunScheduled(long callbackQueryId, String payload) {
        // gc:rs:{sid}
        String sid = payload.substring("gc:rs:".length());

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        config.scheduled = true;
        config.step = "AWAITING_INTERVAL";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Pilih interval pengulangan");

        String html = "📢 <b>GCast Setup</b>\nJeda: " + formatDelay(config.delayMs) +
                " | Filter: " + formatFilters(config.filterModes) +
                "\n\nLangkah 4: Pilih interval pengulangan broadcast";
        editConfig(config, html, buildIntervalKeyboard(sid));
    }

    private void handleRunInterval(long callbackQueryId, String payload) {
        // gc:ri:{sid}:{ms}
        String rest = payload.substring("gc:ri:".length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) return;
        String sid = rest.substring(0, lastColon);
        long intervalMs;
        try {
            intervalMs = Long.parseLong(rest.substring(lastColon + 1));
        } catch (NumberFormatException e) {
            answer(callbackQueryId, "Invalid interval");
            return;
        }

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        config.intervalMs = intervalMs;
        config.step = "RUNNING";
        config.status = "running";
        stateService.saveSession(sid, config);
        answer(callbackQueryId, "Broadcast terjadwal dimulai!");

        String scheduledHtml = "📢 <b>GCast Terjadwal</b>\nSession: " + sid +
                "\nJeda: " + formatDelay(config.delayMs) +
                " | Filter: " + formatFilters(config.filterModes) +
                "\nInterval: " + formatDelay(intervalMs) +
                "\nBroadcast akan berjalan otomatis.";
        editConfig(config, scheduledHtml, buildStopKeyboard(sid));

        final String finalSid = sid;
        final long finalIntervalMs = intervalMs;

        gcastService.scheduleRecurring(finalSid, config, progress -> {
            GcastConfig latest = stateService.getSession(finalSid);
            if (latest == null) return;

            if (progress.startsWith("DONE:")) {
                String counts = progress.substring("DONE:".length());
                String doneHtml = "✅ <b>Broadcast terjadwal selesai!</b>\nSession: " + finalSid +
                        "\nInterval: " + formatDelay(finalIntervalMs) +
                        "\nTerkirim: " + counts;
                editConfig(latest, doneHtml, buildStopKeyboard(finalSid));
            } else if ("CANCELLED".equals(progress)) {
                String cancelHtml = "⏹ <b>Broadcast terjadwal dibatalkan</b>\nSession: " + finalSid;
                editConfig(latest, cancelHtml, buildEmptyKeyboard());
            } else {
                String progressHtml = "📢 <b>Broadcast terjadwal berjalan</b>\nSession: " + finalSid +
                        "\nInterval: " + formatDelay(finalIntervalMs) +
                        "\nProgress: " + progress + " chat";
                editConfig(latest, progressHtml, buildStopKeyboard(finalSid));
            }
        });
    }

    private void handleStop(long callbackQueryId, String payload) {
        // gc:stop:{sid}
        String sid = payload.substring("gc:stop:".length());

        GcastConfig config = stateService.getSession(sid);
        gcastService.cancelBroadcast(sid);
        answer(callbackQueryId, "Menghentikan broadcast...");

        if (config != null) {
            String html = "⏹ <b>Menghentikan broadcast...</b>\nSession: " + sid;
            editConfig(config, html, buildEmptyKeyboard());
        }
    }

    private void handleCancel(long callbackQueryId, String payload) {
        // gc:ca:{sid}
        String sid = payload.substring("gc:ca:".length());

        GcastConfig config = stateService.getSession(sid);
        if (config == null) {
            answer(callbackQueryId, "Sesi tidak ditemukan.");
            return;
        }

        if ("running".equals(config.status)) {
            gcastService.cancelBroadcast(sid);
        }

        stateService.clearAwaitingCron(config.controlChatId);
        stateService.deleteSession(sid);
        answer(callbackQueryId, "Dibatalkan");

        String html = "❌ <b>GCast dibatalkan</b>\nSession: " + sid;
        editConfig(config, html, buildEmptyKeyboard());
    }
}
