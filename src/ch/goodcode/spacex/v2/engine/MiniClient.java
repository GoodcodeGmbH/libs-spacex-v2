/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Paolo Domenighetti
 */
public final class MiniClient {

    private final String remoteServerHost;
    private final int remoteServerPort;
    private Socket socketConnection;
    private ObjectOutputStream clientOutputStream;
    private boolean connected;

    public MiniClient(String remoteServerHost, int remoteServerPort) {
        this.remoteServerHost = remoteServerHost;
        this.remoteServerPort = remoteServerPort;
    }

    public void start() {
        (new Thread(new Runnable() {
            @Override
            public void run() {
                while (!connected) {
                    try {
                        socketConnection = new Socket(remoteServerHost, remoteServerPort);
                        clientOutputStream = new ObjectOutputStream(socketConnection.getOutputStream());
                        connected = true;
                    } catch (Exception ex) {

                    }
                    try {
                        Thread.sleep(10000L);
                    } catch (InterruptedException ex) {
                        
                    }
                }
            }
        })).start();

    }

    public void stop() {
        try {
            clientOutputStream.close();
            socketConnection.close();
        } catch (Exception ex) {
            
        }
    }

    public void sendMessage(EncryptedMessageObject m) throws IOException {
        clientOutputStream.writeObject(m);
    }
}
