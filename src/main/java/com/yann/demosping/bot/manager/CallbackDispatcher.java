package com.yann.demosping.bot.manager;

import it.tdlight.jni.TdApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class CallbackDispatcher {

    public void dispatch(TdApi.UpdateNewCallbackQuery callbackQuery) {
        log.info("Received Callback");
        if (callbackQuery.payload instanceof TdApi.CallbackQueryPayloadData data) {
            log.info("Received Data {}", Arrays.toString(data.data));
        }
    }
}
