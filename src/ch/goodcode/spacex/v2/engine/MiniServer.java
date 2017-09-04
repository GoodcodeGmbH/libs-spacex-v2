/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

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

    public MiniServer(SpaceV2 spaceForCallback, int listeningPort) {
        this.spaceForCallback = spaceForCallback;
        this.listeningPort = listeningPort;
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
                    ClientHandler clientHandler = new ClientHandler(spaceForCallback, pipe);
                    clientHandler.start();
                } catch (IOException ex) {
                    
                }
            }
        });
        theListener.start();

    }

    public void stop() throws IOException {
        theListener.stop();
        socketConnection.close();
    }
}
