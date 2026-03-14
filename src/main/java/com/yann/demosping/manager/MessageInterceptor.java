package com.yann.demosping.manager;

import it.tdlight.jni.TdApi;

public interface MessageInterceptor {

    /**
     * Called before dispatching a message.
     *
     * @return {@code true} to continue the pipeline, {@code false} to stop processing
     */
    boolean preHandle(TdApi.UpdateNewMessage message, String text);
}