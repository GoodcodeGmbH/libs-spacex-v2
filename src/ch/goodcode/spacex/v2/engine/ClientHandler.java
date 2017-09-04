/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

import ch.goodcode.libs.utils.dataspecs.EJSONArray;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import ch.goodcode.spacex.v2.SpaceV2;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

/**
 *
 * @author Paolo Domenighetti
 */
public class ClientHandler extends Thread {

    private final SpaceV2 spaceForCallback;
//    private final MiniServer server;
    private final Socket socket;
    private final ObjectInputStream inputStream;
    private final String clientHost;
    private final int localClientPort;

    public ClientHandler(SpaceV2 spaceForCallback, Socket socket) throws IOException {
        this.spaceForCallback = spaceForCallback;
        this.socket = socket;
        inputStream = new ObjectInputStream(this.socket.getInputStream());
        this.clientHost = socket.getInetAddress().toString();
        this.localClientPort = socket.getPort();
    }

    @Override
    public void run() {
        while (true) {
            try {
                EncryptedMessageObject o = (EncryptedMessageObject) inputStream.readObject();
                handleMessage(o);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(EncryptedMessageObject m) throws Exception {
        int kind = m.getKind();
        switch (kind) {
            case EncryptedMessageObject.KIND_ACCESS:
                EJSONObject access = new EJSONObject();
                // ANNOUNCE <myId> <peerId> (not encrypted)
                if (m.getPayload().contains("ANNOUNCE") && m.getPayload().contains(spaceForCallback.geId())) {
                    String[] split = m.getPayload().split("\\s");
                    access.putString("peer", split[2]);
                    access.putString("remoteHost", clientHost);
                    access.putInteger("remotePort", localClientPort);
                }
                break;
            case EncryptedMessageObject.KIND_META:
                EJSONObject meta = new EJSONObject(decrypt(m.getPayload()));
                spaceForCallback.listen_meta(m.getFromPeer(), meta);
                break;
            case EncryptedMessageObject.KIND_ERROR:
                EJSONObject error = new EJSONObject(decrypt(m.getPayload()));
                spaceForCallback.listen_error(m.getFromPeer(), error);
                break;
            case EncryptedMessageObject.KIND_MOBJECT:
                EJSONArray arr = new EJSONArray(decrypt(m.getPayload()));
                for (int i = 0; i < arr.size(); i++) {
                    EJSONObject object = arr.getObject(i);
                    spaceForCallback.listen_parseAndWorkoutListenedObject(m.getFromPeer(), object);
                }
                break;
            default: // object
                EJSONObject json = new EJSONObject(decrypt(m.getPayload()));
                spaceForCallback.listen_parseAndWorkoutListenedObject(m.getFromPeer(), json);
                break;
        }
    }

    private String decrypt(String p) {
        return "";
    }

}
