package com.yann.demosping.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExecMessageStore {

    // resultId → [chatId, msgId] — for Delete button lookups
    private final ConcurrentHashMap<String, long[]> resultToMsg = new ConcurrentHashMap<>();

    // cmdMsgId → [chatId, resultMsgId]
    private final ConcurrentHashMap<Long, long[]> cmdToResult = new ConcurrentHashMap<>();

    // cmdMsgId → resultId (current inline result ID for this command message)
    private final ConcurrentHashMap<Long, String> cmdToResultId = new ConcurrentHashMap<>();

    // cmdMsgId → cmd string
    private final ConcurrentHashMap<Long, String> cmdToStr = new ConcurrentHashMap<>();

    /** Called after SendInlineQueryResultMessage completes. */
    public void storeResult(String resultId, long chatId, long msgId) {
        resultToMsg.put(resultId, new long[]{chatId, msgId});
    }

    public long[] getByResultId(String resultId) {
        return resultToMsg.get(resultId);
    }

    public void removeByResultId(String resultId) {
        resultToMsg.remove(resultId);
    }

    /**
     * Track which result message corresponds to which command message.
     * Also cleans up the old resultId entry from resultToMsg when the command is updated.
     */
    public void trackCmd(long cmdMsgId, long chatId, long resultMsgId, String resultId, String cmd) {
        String oldResultId = cmdToResultId.put(cmdMsgId, resultId);
        if (oldResultId != null && !oldResultId.equals(resultId)) {
            resultToMsg.remove(oldResultId);
        }
        cmdToResult.put(cmdMsgId, new long[]{chatId, resultMsgId});
        cmdToStr.put(cmdMsgId, cmd);
    }

    public long[] getResultForCmd(long cmdMsgId) {
        return cmdToResult.get(cmdMsgId);
    }

    public String getResultId(long cmdMsgId) {
        return cmdToResultId.get(cmdMsgId);
    }

    public String getCmdStr(long cmdMsgId) {
        return cmdToStr.get(cmdMsgId);
    }

    public void removeCmd(long cmdMsgId) {
        String resultId = cmdToResultId.remove(cmdMsgId);
        if (resultId != null) resultToMsg.remove(resultId);
        cmdToResult.remove(cmdMsgId);
        cmdToStr.remove(cmdMsgId);
    }
}
