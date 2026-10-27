package model;

import java.time.LocalDateTime;

public class Message {

    public static final String TEXT = "text";
    public static final String IMAGE = "image";
    public static final String VIDEO = "video";

    private final int messageId;
    private final int senderId;
    private final int receiverId;
    private final String body;
    private final String kind;
    private final String fileName;
    private final long fileSize;
    private final boolean pinned;
    private final LocalDateTime sentAt;
    private final LocalDateTime readAt;

    public Message(int messageId, int senderId, int receiverId, String body, String kind, String fileName,
                   long fileSize, boolean pinned, LocalDateTime sentAt, LocalDateTime readAt) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.body = body;
        this.kind = kind;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pinned = pinned;
        this.sentAt = sentAt;
        this.readAt = readAt;
    }

    public int getMessageId() {
        return messageId;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public String getBody() {
        return body;
    }

    public String getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean isPinned() {
        return pinned;
    }

    public boolean hasFile() {
        return !kind.equals(TEXT);
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
