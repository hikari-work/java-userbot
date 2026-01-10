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
    private int width;
    private int height;
    private float scale;
    private List<QuotlyMessage> messages;

    @Data @Builder
    public static class QuotlyMessage {
        // PERBAIKAN: Inisialisasi default agar jadi [] bukan null di JSON
        @Builder.Default
        private List<QuotlyEntity> entities = new ArrayList<>();

        private boolean avatar;
        private QuotlySender from;
        private String text;
        private QuotlyMessage replyMessage;
    }

    // ... sisa class QuotlySender, QuotlyPhoto, QuotlyEntity sama ...
    @Data @Builder
    public static class QuotlySender {
        private long id;
        private String name;
        private String title;
        private QuotlyPhoto photo;
    }

    @Data @Builder
    public static class QuotlyPhoto {
        private String url;
    }

    @Data @Builder
    public static class QuotlyEntity {
        private String type;
        private int offset;
        private int length;
    }
}