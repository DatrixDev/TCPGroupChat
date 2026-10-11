package com.tcpgroupchat.common.protocol;

/** JSON-line protocol message exchanged between a TCP client and the server. */
public class ChatMessage {
    private String type;
    private String sender;
    private String recipient;
    private String group;
    private String content;

    public ChatMessage() {}

    public ChatMessage(String type, String sender, String recipient, String group, String content) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.group = group;
        this.content = content;
    }

    public static ChatMessage of(String type, String sender, String content) {
        return new ChatMessage(type, sender, null, null, content);
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
