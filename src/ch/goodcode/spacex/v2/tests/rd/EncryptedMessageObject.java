/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.tests.rd;

import java.io.Serializable;

/**
 *
 * @author Paolo Domenighetti
 */
public class EncryptedMessageObject implements Serializable {
    
    public static final int KIND_OBJECT = 0;
    public static final int KIND_MOBJECT = 3;
    public static final int KIND_META = 9;
    public static final int KIND_ACCESS = 1;
    public static final int KIND_ERROR = 2;
    
    
    private final String uid;
    private final int kind;
    private final String payload;
    private final String fromPeer;
    private final long issued;

    public EncryptedMessageObject(String uid, int kind, String payload, String fromPeer, long issued) {
        this.uid = uid;
        this.kind = kind;
        this.payload = payload;
        this.fromPeer = fromPeer;
        this.issued = issued;
    }

    public String getUid() {
        return uid;
    }

    public int getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public String getFromPeer() {
        return fromPeer;
    }

    public long getIssued() {
        return issued;
    }
    
}
