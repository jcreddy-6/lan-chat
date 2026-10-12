package com.lanchat.common;

/** A file transfer packet. The file bytes travel inside the object. */
public class FileMessage extends Message {
    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final byte[] data;
    private final boolean privateFile;   // true when sent to one user instead of a room

    public FileMessage(String sender, String receiver, String fileName, byte[] data) {
        this(sender, receiver, fileName, data, false);
    }

    public FileMessage(String sender, String receiver, String fileName, byte[] data, boolean privateFile) {
        super(MessageType.FILE, sender, receiver);
        this.fileName = fileName;
        this.data = data;
        this.privateFile = privateFile;
    }

    public String getFileName() { return fileName; }

    public byte[] getData() { return data; }

    public boolean isPrivate() { return privateFile; }

    @Override
    public String display() {
        int kb = Math.max(1, data.length / 1024);
        return getSender() + " sent a file: " + fileName + " (" + kb + " KB)";
    }
}
