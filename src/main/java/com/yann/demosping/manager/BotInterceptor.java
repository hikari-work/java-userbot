package com.yann.demosping.manager;

import it.tdlight.jni.TdApi;

public interface BotInterceptor {

    boolean preHandle(TdApi.UpdateNewMessage message, String args);
}
