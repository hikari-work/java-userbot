package com.yann.demosping.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GcastConfig {

    public long sourceChatId;
    public long sourceMessageId;

    public long controlChatId;
    public long controlMessageId;
    public String controlInlineMessageId;

    public long delayMs;

    public Set<String> filterModes;

    public int folderId;
    public String folderName;

    public String labelName;

    public boolean scheduled;
    public long intervalMs;

    public boolean sendViaBot;      // send via inline bot (shows "via @botname")
    public boolean noForwardHeader; // send as copy without "Forwarded from" header

    public String status;
    public String step;

    public List<Long> sentChatIds;

    public int totalChats;

    public GcastConfig() {
        this.filterModes = new HashSet<>();
        this.sentChatIds = new ArrayList<>();
        this.status = "pending";
        this.step = "DELAY";
    }
}
