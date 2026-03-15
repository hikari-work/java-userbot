package com.yann.demosping.manager;

import it.tdlight.jni.TdApi;
import reactor.core.publisher.Mono;

public interface MessageInterceptor {

    /**
     * Called before dispatching a message.
     *
     * @return {@code Mono<true>} to continue the pipeline, {@code Mono<false>} to stop processing
     */
    Mono<Boolean> preHandle(TdApi.UpdateNewMessage message, String text);
}
