package com.yann.demosping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImgBBResponse {
    private ImgBBData data;
    private boolean success;
    private int status;

    @Data
    @NoArgsConstructor
    public static class ImgBBData {
        private String id;
        private String url;
        @JsonProperty("display_url")
        private String displayUrl;
        @JsonProperty("delete_url")
        private String deleteUrl;
    }
}