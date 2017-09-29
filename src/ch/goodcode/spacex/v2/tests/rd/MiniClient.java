/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.tests.rd;

import ch.goodcode.libs.logging.LogBuffer;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 *
 * @author Paolo Domenighetti
 */
public final class MiniClient {

    private final SpaceV2_RD spaceForCallback;
    private final String remoteServerHost;
    private final int remoteServerPort;
    private Socket socketConnection;
    private ObjectOutputStream clientOutputStream;
    private boolean connected;
    private final LogBuffer LOG;
    private final String remotePeerId;

    public MiniClient(SpaceV2_RD spaceForCallback, String remoteServerHost, int remoteServerPort, String remotePeer, LogBuffer LOG) {
        this.remoteServerHost = remoteServerHost;
        this.remoteServerPort = remoteServerPort;
        this.LOG = LOG;
        this.spaceForCallback = spaceForCallback;
        this.remotePeerId = remotePeer;
    }

    public void start() {
        LOG.o("MiniClient for peer '"+remotePeerId+"' started.");
       launchConnectorThread();
    }

    public void stop() {
        try {
            clientOutputStream.close();
            socketConnection.close();
            LOG.o("MiniClient for peer '"+remotePeerId+"' properly disposed.");
        } catch (IOException ex) {
            LOG.e("Error disposing with MiniClient.stop() for " + remoteServerHost + ":" + remoteServerPort, ex);
        }
    }

    public boolean sendMessage(EncryptedMessageObject m) {
        if (connected) {
            try {
                clientOutputStream.writeObject(m);
                LOG.i("MiniClient for peer '"+remotePeerId+"' sent "+m.getUid());
                return true;
            } catch (IOException ex) {
                LOG.e("I/O Issue in MiniClient.sendMessage() for " + remoteServerHost + ":" + remoteServerPort, ex);
                connected = false;
                launchConnectorThread();
                return false;
            }
        } else {
            LOG.e("I/O Issue in MiniClient.sendMessage() for " + remoteServerHost + ":" + remoteServerPort+"; the client is disconencted!");
            launchConnectorThread();
            return false;
        }
    }
    
    private void launchConnectorThread() {
         (new Thread(() -> {
            while (!connected) {
                try {
                    socketConnection = new Socket(remoteServerHost, remoteServerPort);
                    clientOutputStream = new ObjectOutputStream(socketConnection.getOutputStream());
                    connected = true;
                    LOG.o("MiniClient connection worker has connected to "+remoteServerHost+":"+remoteServerPort+".");
                    EncryptedMessageObject announce = spaceForCallback.issueMessage_ANNOUNCE_request(remotePeerId);
                    sendMessage(announce);
                } catch (Exception ex) {
                    LOG.i("MiniClient connection worker: "+remoteServerHost+":"+remoteServerPort+" unreachable, retrying in 10 sec.");
                }
                try {
                    Thread.sleep(10000L);
                } catch (InterruptedException ex) {

                }
            }
        })).start();
    }
}
