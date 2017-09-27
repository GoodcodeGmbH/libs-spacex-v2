/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

import ch.goodcode.libs.logging.LogBuffer;
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
public class PeerHandler extends Thread {

    private final SpaceV2 spaceForCallback;
    private final Socket socket;
    private final ObjectInputStream inputStream;
    private final String clientHost;
    private final int localClientPort;
    private final LogBuffer LOG;
    private String remotePeerId;

    public PeerHandler(SpaceV2 spaceForCallback, Socket socket, LogBuffer LOG) throws IOException {
        this.spaceForCallback = spaceForCallback;
        this.socket = socket;
        inputStream = new ObjectInputStream(this.socket.getInputStream());
        this.clientHost = socket.getInetAddress().toString();
        this.localClientPort = socket.getPort();
        this.LOG = LOG;
    }

    @Override
    public void run() {
        while (true) {
            try {
                EncryptedMessageObject o = (EncryptedMessageObject) inputStream.readObject();
                handleMessage(o);
            } catch (Exception e) {
                LOG.i("Error in ClientHandler loop listening for " + clientHost);
            }
        }
    }

    private void handleMessage(EncryptedMessageObject m) throws Exception {
        int kind = m.getKind();
        switch (kind) {
            case EncryptedMessageObject.KIND_ACCESS:
                String payload = spaceForCallback.decrypt(null, m.getPayload());
                if (payload != null && payload.equals(spaceForCallback.geSpaceId())) {
                    // ANNOUNCE REQUEST
                    remotePeerId = m.getFromPeer(); // <<==== this handler is now assigned to the remote peer!
                    final String k = spaceForCallback.issueAndRegisterSessionKeyForPeer(remotePeerId);
                    EncryptedMessageObject nm = spaceForCallback.issueMessage_ANNOUNCE_answer(k);
                    spaceForCallback.sendMessageToPeer(remotePeerId, nm);
                } else if (payload != null && payload.startsWith("KEYED:")) {
                    // ANNOUNCE ANSWER
                    boolean announced = spaceForCallback.saveAndRegisterSessionKeyForPeer(remotePeerId, payload.split(":")[1]);
                    if (announced) {
                        EncryptedMessageObject nm = spaceForCallback.issueMessage_LOGIN_request(remotePeerId);
                        spaceForCallback.sendMessageToPeer(remotePeerId, nm);
                    } else {
                        // the other peer do not know me, abort everything
                    }
                } else {
                    payload = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                    if (payload != null && payload.startsWith(remotePeerId)) {
                        // LOGIN REQUEST
                        // it is a login, payload is still encrypted because it has new encryption key
                        boolean ans = spaceForCallback.listen_login(remotePeerId, payload);
                        spaceForCallback.issueMessage_LOGIN_answer(remotePeerId, ans);
                    } else if (payload != null) {
                        // LOGIN ANSWER
                        if (payload.equals("OK")) {
                            // i'am logged to other's server
                        } else {
                            // nope
                        }
                    } else {
                        // security breach/issue/error
                    }
                }
                break;
            case EncryptedMessageObject.KIND_META:
                String decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());

                if (decrypted != null) {
                    EJSONObject meta = new EJSONObject(decrypted);
                    spaceForCallback.listen_meta(m.getFromPeer(), meta);
                } else {
                    // security breach/issue/error
                }
                break;
            case EncryptedMessageObject.KIND_ERROR:
                decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                if (decrypted != null) {
                    EJSONObject error = new EJSONObject(decrypted);
                    spaceForCallback.listen_error(m.getFromPeer(), error);
                } else {

                }
                break;
            case EncryptedMessageObject.KIND_MOBJECT:
                decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                if (decrypted != null) {
                    EJSONArray arr = new EJSONArray(decrypted);
                    for (int i = 0; i < arr.size(); i++) {
                        EJSONObject object = arr.getObject(i);
                        spaceForCallback.listen_parseAndWorkoutListenedObject(remotePeerId, object);
                    }
                } else {
                    // security breach/issue/error
                }
                break;
            default: // object
                decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                if (decrypted != null) {
                    EJSONObject json = new EJSONObject(decrypted);
                    spaceForCallback.listen_parseAndWorkoutListenedObject(m.getFromPeer(), json);
                } else {
                    // security breach/issue/error
                }
                break;
        }
    }

}
