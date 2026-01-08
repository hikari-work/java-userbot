package com.yann.demosping.utils;

import com.yann.demosping.service.dramabox.DramaBoxSearchResult;
import it.tdlight.jni.TdApi;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class InlineHelper {

    public TdApi.InputInlineQueryResult[] createSearch(List<DramaBoxSearchResult> searchResultList) {
        List<TdApi.InputInlineQueryResult> results = new ArrayList<>();
        for (DramaBoxSearchResult drama : searchResultList) {
            if (drama.getBookId() != null && drama.getBookName() != null) {
                results.add(createSingleResult(drama));
            }
        }
        return results.toArray(new TdApi.InputInlineQueryResult[0]);
    }
    private TdApi.InputInlineQueryResult createSingleResult(DramaBoxSearchResult result) {
        String id = result.getBookId();
        String title = result.getBookName();
        String introduction = result.getIntroduction();
        String thumbUrl = result.getCover();

        String description = (introduction != null && introduction.length() > 50)
                ? introduction.substring(0, 50) + "..."
                : introduction;
        String headerPrefix = "📖 ";
        String textBody = headerPrefix + title + "\n\n" + introduction;

        List<TdApi.TextEntity> entities = new ArrayList<>();

        entities.add(new TdApi.TextEntity(
                headerPrefix.length(),
                title.length(),
                new TdApi.TextEntityTypeBold()
        ));

        TdApi.LinkPreviewOptions linkPreviewOptions = new TdApi.LinkPreviewOptions(
                false,
                thumbUrl,
                true,
                true,
                true
        );

        TdApi.InputMessageContent content = new TdApi.InputMessageText(
                new TdApi.FormattedText(
                        textBody,
                        entities.toArray(new TdApi.TextEntity[0])
                ),
                linkPreviewOptions,
                false
        );

        TdApi.ReplyMarkup replyMarkup = getReplyMarkup(id);
        return new TdApi.InputInlineQueryResultArticle(
                id,
                null,
                title,
                description,
                thumbUrl,
                0,
                0,
                replyMarkup,
                content
        );
    }

    private static TdApi.@NonNull ReplyMarkup getReplyMarkup(String id) {
        String payloadData = "OPEN_DRAMA:" + id;
        return new TdApi.ReplyMarkupInlineKeyboard(
                new TdApi.InlineKeyboardButton[][] {
                        {
                                new TdApi.InlineKeyboardButton(
                                        "Buka Detail 📂",
                                        new TdApi.InlineKeyboardButtonTypeCallback((payloadData + id).getBytes(StandardCharsets.UTF_8))
                                )
                        }
                }
        );
    }

}
