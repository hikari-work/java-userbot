package com.yann.demosping.plugin.coomer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostDTO {

    private String id;
    private FileDTO file;
    private List<FileDTO> attachments;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FileDTO {
        private String name;
        private String path;
    }
}
