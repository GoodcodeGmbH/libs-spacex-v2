/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

/**
 *
 * @author pdome
 */
public class DeleteMessage {
    
    private long issued;
    
    private String targetClazz;
    private String targetUid;
    private String peerOrigin;

    public long getIssued() {
        return issued;
    }

    public void setIssued(long issued) {
        this.issued = issued;
    }

    public String getTargetClazz() {
        return targetClazz;
    }

    public void setTargetClazz(String targetClazz) {
        this.targetClazz = targetClazz;
    }

    public String getTargetUid() {
        return targetUid;
    }

    public void setTargetUid(String targetUid) {
        this.targetUid = targetUid;
    }

    public String getPeerOrigin() {
        return peerOrigin;
    }

    public void setPeerOrigin(String peerOrigin) {
        this.peerOrigin = peerOrigin;
    }
    
    
    
}
