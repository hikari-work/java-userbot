package com.yann.demosping.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuotlyRequest {
    @Builder.Default private String type = "quote";
    @Builder.Default private String format = "webp";
    @Builder.Default private String backgroundColor = "#1b1429";
    private Integer width;
    private Integer height;
    private Float scale;
    /** Emoji style: apple (default), google, twitter, etc. */
    private String emojiBrand;
    private List<QuotlyMessage> messages;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuotlyMessage {
        @Builder.Default
        private List<QuotlyEntity> entities = new ArrayList<>();
        private boolean avatar;
        private QuotlySender from;
        private String text;
        /** "sticker" or omit for normal message */
        private String mediaType;
        private QuotlyMedia media;
        private QuotlyReplyMessage replyMessage;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuotlySender {
        private long id;
        private String name;
        private String username;
        private QuotlyPhoto photo;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuotlyReplyMessage {
        private String name;
        private String text;
        @Builder.Default
        private List<QuotlyEntity> entities = new ArrayList<>();
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuotlyPhoto {
        private String url;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuotlyMedia {
        private String url;
        private Integer width;
        private Integer height;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuotlyEntity {
        private String type;
        private int offset;
        private int length;
        /** For type=text_link */
        private String url;
        /** For type=pre */
        private String language;
    }
}
