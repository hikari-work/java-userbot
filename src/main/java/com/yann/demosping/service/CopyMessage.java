package com.yann.demosping.service;

import it.tdlight.jni.TdApi;
import org.springframework.stereotype.Component;


@Component
public class CopyMessage {

    public TdApi.InputMessageContent convertToInput(TdApi.MessageContent content) {
        switch (content) {
            case TdApi.MessageText text -> {
                return new TdApi.InputMessageText(text.text, new TdApi.LinkPreviewOptions(), false);
            }
            case TdApi.MessagePhoto photo -> {
                TdApi.PhotoSize maxPhoto = photo.photo.sizes[photo.photo.sizes.length - 1];
                return new TdApi.InputMessagePhoto(
                        new TdApi.InputFileRemote(maxPhoto.photo.remote.id),
                        null,
                        new int[0],
                        maxPhoto.width,
                        maxPhoto.height,
                        photo.caption,
                        photo.hasSpoiler,
                        null,
                        false
                );
            }
            case TdApi.MessageVideo video -> {
                return new TdApi.InputMessageVideo(
                        new TdApi.InputFileRemote(video.video.video.remote.id),
                        video.video.thumbnail != null ?
                                new TdApi.InputThumbnail(
                                        new TdApi.InputFileRemote(video.video.thumbnail.file.remote.id),
                                        video.video.thumbnail.width,
                                        video.video.thumbnail.height
                                ) : null,
                        null,
                        video.video.duration,
                        new int[0],
                        video.video.height,
                        video.video.width,
                        0,
                        video.video.supportsStreaming,
                        video.caption,
                        video.hasSpoiler,
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
            case TdApi.MessageAnimation animation -> {
                return new TdApi.InputMessageAnimation(
                        new TdApi.InputFileRemote(animation.animation.animation.remote.id),
                        animation.animation.thumbnail != null ?
                                new TdApi.InputThumbnail(
                                        new TdApi.InputFileRemote(animation.animation.thumbnail.file.remote.id),
                                        animation.animation.thumbnail.width,
                                        animation.animation.thumbnail.height
                                ) : null,
                        new int[0],
                        animation.animation.duration,
                        animation.animation.width,
                        animation.animation.height,
                        animation.caption,
                        animation.hasSpoiler,
                        false
                );
            }
            case TdApi.MessageAudio audio -> {
                return new TdApi.InputMessageAudio(
                        new TdApi.InputFileRemote(audio.audio.audio.remote.id),
                        audio.audio.albumCoverThumbnail != null ?
                                new TdApi.InputThumbnail(
                                        new TdApi.InputFileRemote(audio.audio.albumCoverThumbnail.file.remote.id),
                                        audio.audio.albumCoverThumbnail.width,
                                        audio.audio.albumCoverThumbnail.height
                                ) : null,
                        audio.audio.duration,
                        audio.audio.title,
                        audio.audio.performer,
                        audio.caption
                );
            }
            case TdApi.MessageDocument document -> {
                return new TdApi.InputMessageDocument(
                        new TdApi.InputFileRemote(document.document.document.remote.id),
                        document.document.thumbnail != null ?
                                new TdApi.InputThumbnail(
                                        new TdApi.InputFileRemote(document.document.thumbnail.file.remote.id),
                                        document.document.thumbnail.width,
                                        document.document.thumbnail.height
                                ) : null,
                        false,
                        document.caption
                );
            }
            case TdApi.MessageVoiceNote voice -> {
                return new TdApi.InputMessageVoiceNote(
                        new TdApi.InputFileRemote(voice.voiceNote.voice.remote.id),
                        voice.voiceNote.duration,
                        voice.voiceNote.waveform,
                        voice.caption,
                        null
                );
            }
            case TdApi.MessageVideoNote videoNote -> {
                return new TdApi.InputMessageVideoNote(
                        new TdApi.InputFileRemote(videoNote.videoNote.video.remote.id),
                        videoNote.videoNote.thumbnail != null ?
                                new TdApi.InputThumbnail(
                                        new TdApi.InputFileRemote(videoNote.videoNote.thumbnail.file.remote.id),
                                        videoNote.videoNote.thumbnail.width,
                                        videoNote.videoNote.thumbnail.height
                                ) : null,
                        videoNote.videoNote.duration,
                        videoNote.videoNote.length,
                        null
                );
            }
            case null, default -> {
                return null;
            }
        }
    }
}