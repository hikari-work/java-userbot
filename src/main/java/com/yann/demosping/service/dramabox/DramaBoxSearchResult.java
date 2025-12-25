package com.yann.demosping.service.dramabox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DramaBoxSearchResult {
    private String bookId;
    private String bookName;
    private String introduction;
    private String author;
    private String cover;

}
