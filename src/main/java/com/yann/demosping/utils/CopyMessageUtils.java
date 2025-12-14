package com.yann.demosping.utils;

import it.tdlight.jni.TdApi;

public class CopyMessageUtils {

    public static TdApi.InputMessageContent convertToInput(TdApi.MessageContent content) {
        switch (content) {
            case TdApi.MessageText text -> {
                return new TdApi.InputMessageText(text.text, new TdApi.LinkPreviewOptions(), false);
            }
            case TdApi.MessagePhoto photos -> {
                TdApi.File file = photos.photo.sizes[photos.photo.sizes.length - 1].photo;
                return new TdApi.InputMessagePhoto(
                        new TdApi.InputFileRemote(
                                file.remote.id
                        ), null,
                        null,
                        photos.photo.sizes[photos.photo.sizes.length - 1].width,
                        photos.photo.sizes[photos.photo.sizes.length - 1].height,
                        photos.caption,
                        false,
                        null,
                        false
                );
            }
            case TdApi.MessageVideo video -> {
                return new TdApi.InputMessageVideo(
                        new TdApi.InputFileRemote(video.video.video.remote.id),
                        null,
                        null,
                        video.video.duration,
                        null,
                        video.video.height,
                        video.video.width,
                        0,
                        false,
                        video.caption,
                        false,
                        null,
                        false
                );
            }
            case TdApi.MessageSticker sticker -> {
                return new TdApi.InputMessageSticker(
                        new TdApi.InputFileRemote(sticker.sticker.sticker.remote.id),
                        null,
                        sticker.sticker.width,
                        sticker.sticker.height,
                        null
                );
            }
            case null, default -> {
                return null;
            }
        }
    }
}
