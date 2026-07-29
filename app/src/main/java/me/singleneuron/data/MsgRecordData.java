package me.singleneuron.data;

/**
 * Minimal stub for MsgRecordData. Used by MessageManager/MessageReceiver.
 */
public class MsgRecordData {
    private final long msgUid;

    public MsgRecordData(long msgUid) {
        this.msgUid = msgUid;
    }

    public long getMsgUid() {
        return msgUid;
    }
}
