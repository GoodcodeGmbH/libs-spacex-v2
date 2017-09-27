/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

import ch.goodcode.libs.logging.LogBuffer;
import ch.goodcode.spacex.v2.SpaceV2;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author Paolo Domenighetti
 */
public final class MiniServer {

    private final SpaceV2 spaceForCallback;
    private final int listeningPort;
    private ServerSocket socketConnection;
    private Thread theListener;
    private final LogBuffer LOG;

    public MiniServer(SpaceV2 spaceForCallback, int listeningPort, LogBuffer LOG) {
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
                    PeerHandler clientHandler = new PeerHandler(spaceForCallback, pipe, LOG);
                    clientHandler.start();
                } catch (IOException ex) {
                    LOG.e("I/O Issue in MiniServer.startInThread() loop listening on "+listeningPort, ex);
                }
            }
        });
        theListener.start();

    }

    public void stop() {
        try {
            theListener.stop();
            socketConnection.close();
        } catch (IOException ex) {
            LOG.e("Error disposing with MiniServer.stop() listening on "+listeningPort, ex);
        }
    }
}
