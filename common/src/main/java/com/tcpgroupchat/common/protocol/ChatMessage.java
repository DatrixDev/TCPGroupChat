package com.tcpgroupchat.common.protocol;

/** JSON-line protocol message exchanged between a TCP client and the server. */
public class ChatMessage {
  private String requestId;
  private String password;
  private String sessionId;
  private String timestamp;
  private java.util.List<String> users;
  private java.util.List<String> members;
  private java.util.List<String> joinedGroups;
  private String type;
  private String sender;
  private String recipient;
  private String group;
  private String groupCreator;
  private String content;

  private String senderIp;
  private String attachmentName;
  private String attachmentType;
  private String attachmentData;
  private long attachmentSize;
  private String groupIp, broadcastIp, networkIp;
  private int udpPort, groupPort;


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

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String value) {
    requestId = value;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String value) {
    password = value;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String value) {
    sessionId = value;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String value) {
    timestamp = value;
  }

  public java.util.List<String> getUsers() {
    return users;
  }

  public void setUsers(java.util.List<String> value) {
    users = value;
  }

  public java.util.List<String> getMembers() {
    return members;
  }

  public void setMembers(java.util.List<String> value) {
    members = value;
  }

  public java.util.List<String> getJoinedGroups() {
    return joinedGroups;
  }

  public void setJoinedGroups(java.util.List<String> value) {
    joinedGroups = value;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getSender() {
    return sender;
  }

  public void setSender(String sender) {
    this.sender = sender;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public String getGroupCreator() { return groupCreator; }

  public void setGroupCreator(String value) { groupCreator = value; }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getSenderIp() { return senderIp; }
  public void setSenderIp(String value) { senderIp = value; }
  public String getAttachmentName() { return attachmentName; }
  public void setAttachmentName(String value) { attachmentName = value; }
  public String getAttachmentType() { return attachmentType; }
  public void setAttachmentType(String value) { attachmentType = value; }
  public String getAttachmentData() { return attachmentData; }
  public void setAttachmentData(String value) { attachmentData = value; }
  public long getAttachmentSize() { return attachmentSize; }
  public void setAttachmentSize(long value) { attachmentSize = value; }

  public boolean hasAttachment() {
    return attachmentName != null && !attachmentName.isBlank()
        && attachmentData != null && !attachmentData.isBlank();
  }
}
