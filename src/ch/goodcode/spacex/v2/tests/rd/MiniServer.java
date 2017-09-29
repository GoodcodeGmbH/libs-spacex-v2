/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.tests.rd;

import ch.goodcode.libs.logging.LogBuffer;
import ch.goodcode.spacex.v2.SpaceV2;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

/**
 *
 * @author Paolo Domenighetti
 */
public final class MiniServer {

    private final SpaceV2_RD spaceForCallback;
    private final int listeningPort;
    private ServerSocket socketConnection;
    private Thread theListener;
    private final LogBuffer LOG;
    private final HashMap<String,PeerHandler> handlers = new HashMap<>();

    public MiniServer(SpaceV2_RD spaceForCallback, int listeningPort, LogBuffer LOG) {
        this.spaceForCallback = spaceForCallback;
        this.listeningPort = listeningPort;
        this.LOG = LOG;
    }

    public ServerSocket getSocketConnection() {
        return socketConnection;
    }

    public void startInThread() throws IOException {
        socketConnection = new ServerSocket(listeningPort);
        theListener = new Thread(() -> {
            while (true) {
                try {
                    Socket pipe = socketConnection.accept();
                    String strangeId = pipe.getRemoteSocketAddress().toString()+System.currentTimeMillis();
                    PeerHandler clientHandler = new PeerHandler(spaceForCallback, this, strangeId, pipe, LOG);
                    handlers.put(strangeId, clientHandler);
                    LOG.o("MiniServer listened incoming socket ["+strangeId+"], starting its handler.");
                    clientHandler.start();
                } catch (IOException ex) {
                    LOG.e("I/O Issue in MiniServer.startInThread() loop listening on "+listeningPort, ex);
                }
            }
        });
        theListener.start();

    }
    
    public void markDeadPeerHandler(String strangeId) {
        // the thread has will be terminated after this lines
        handlers.remove(strangeId);
        LOG.i("MiniServer.markDeadPeerHandler cleane dup "+strangeId+".");
    }

    public void stop() {
        try {
            theListener.stop();
            socketConnection.close();
            LOG.o("MiniServer properly disposed.");
        } catch (IOException ex) {
            LOG.e("Error disposing with MiniServer.stop() listening on "+listeningPort, ex);
        }
    }
}
