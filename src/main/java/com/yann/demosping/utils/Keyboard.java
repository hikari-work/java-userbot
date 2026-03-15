package com.yann.demosping.utils;

import it.tdlight.jni.TdApi;

import java.util.List;

/** Factory helpers for building TDLib inline keyboards. */
public final class Keyboard {

    private Keyboard() {}

    public static TdApi.InlineKeyboardButton callbackBtn(String text, String data) {
        TdApi.InlineKeyboardButtonTypeCallback type = new TdApi.InlineKeyboardButtonTypeCallback();
        type.data = data.getBytes();
        return btn(text, type);
    }

    public static TdApi.InlineKeyboardButton urlBtn(String text, String url) {
        TdApi.InlineKeyboardButtonTypeUrl type = new TdApi.InlineKeyboardButtonTypeUrl();
        type.url = url;
        return btn(text, type);
    }

    public static TdApi.InlineKeyboardButton btn(String text, TdApi.InlineKeyboardButtonType type) {
        TdApi.InlineKeyboardButton button = new TdApi.InlineKeyboardButton();
        button.text = text;
        button.type = type;
        return button;
    }

    /** Build a keyboard from vararg rows. */
    public static TdApi.ReplyMarkupInlineKeyboard of(TdApi.InlineKeyboardButton[]... rows) {
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows;
        return keyboard;
    }

    /** Build a keyboard from a list of rows (for dynamically assembled keyboards). */
    public static TdApi.ReplyMarkupInlineKeyboard ofRows(List<TdApi.InlineKeyboardButton[]> rows) {
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = rows.toArray(new TdApi.InlineKeyboardButton[0][]);
        return keyboard;
    }

    /** Empty keyboard — removes all buttons. */
    public static TdApi.ReplyMarkupInlineKeyboard empty() {
        TdApi.ReplyMarkupInlineKeyboard keyboard = new TdApi.ReplyMarkupInlineKeyboard();
        keyboard.rows = new TdApi.InlineKeyboardButton[0][0];
        return keyboard;
    }
}
