/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

/**
 *
 * @author Paolo Domenighetti
 */
public class SpacePeer {
    
    private String uid;
    private String host;
    private int port;
    private boolean connected;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    
    
}
