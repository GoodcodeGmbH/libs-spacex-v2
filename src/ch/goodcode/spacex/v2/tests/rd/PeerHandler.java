/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.tests.rd;

import ch.goodcode.libs.logging.LogBuffer;
import ch.goodcode.libs.utils.dataspecs.EJSONArray;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import ch.goodcode.spacex.v2.tests.SpaceV2Debug;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

/**
 *
 * @author Paolo Domenighetti
 */
public class PeerHandler extends Thread {

    private final MiniServer serverForCallback;
    private final String serverStrangeId;
    private final SpaceV2_RD spaceForCallback;
    private final Socket socket;
    private final ObjectInputStream inputStream;
    private final String clientHost;
    private final LogBuffer LOG;
    private String remotePeerId;
    private boolean go = true;

    public PeerHandler(SpaceV2_RD spaceForCallback, MiniServer serverForCallback, String serverStrangeId, Socket socket, LogBuffer LOG) throws IOException {
        this.spaceForCallback = spaceForCallback;
        this.socket = socket;
        inputStream = new ObjectInputStream(this.socket.getInputStream());
        this.clientHost = socket.getInetAddress().toString();
        this.LOG = LOG;
        this.serverForCallback = serverForCallback;
        this.serverStrangeId = serverStrangeId;
    }

    @Override
    public void run() {
        while (go) {
            try {
                EncryptedMessageObject o = (EncryptedMessageObject) inputStream.readObject();
                LOG.i("PeerHandler for peer '" + remotePeerId + "' received " + o.getUid());
                handleMessage(o);
            } catch (Exception e) {
                LOG.o("Connection lost in PeerHandler loop listening for peer '" + remotePeerId + "' (" + clientHost + ") [" + serverStrangeId + "]. "
                        + "This PeerHandler will be terminated and a new one will be issued if necessary when the peer will come in again.");
                go = false;
                serverForCallback.markDeadPeerHandler(serverStrangeId);
                spaceForCallback.markDisconnectedForOUT(remotePeerId);
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
                    LOG.i("PeerHandler.handleMessage() for peer '" + remotePeerId + "' ANNOUNCE received and ANNOUNCE ANSWER sent.");
                } else if (payload != null && payload.startsWith("KEYED:")) {
                    // ANNOUNCE ANSWER
                    boolean announced = spaceForCallback.saveAndRegisterSessionKeyForPeer(remotePeerId, payload.split(":")[1]);
                    if (announced) {
                        EncryptedMessageObject nm = spaceForCallback.issueMessage_LOGIN_request(remotePeerId);
                        spaceForCallback.sendMessageToPeer(remotePeerId, nm);
                        LOG.i("PeerHandler.handleMessage() for peer '" + remotePeerId + "' ANNOUNCE ANSWER received and LOGIN REQUEST sent.");
                    } else {
                        // the other peer do not know me, abort everything
                        LOG.e("PeerHandler.handleMessage() for peer '" + remotePeerId + "' ANNOUNCE received but the peer is UNKNOWN! The handler will be terminated.");
                        serverForCallback.markDeadPeerHandler(serverStrangeId);
                        spaceForCallback.markDisconnectedForOUT(remotePeerId);
                        go = false;
                    }
                } else {
                    payload = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                    if (payload != null && payload.startsWith(remotePeerId)) {
                        // LOGIN REQUEST
                        // it is a login, payload is still encrypted because it has new encryption key
                        boolean ans = spaceForCallback.listen_login(remotePeerId, payload);
                        spaceForCallback.issueMessage_LOGIN_answer(remotePeerId, ans);
                        if (ans) {
                            spaceForCallback.markConnectedForOUT(remotePeerId);
                            LOG.i("PeerHandler.handleMessage() for peer '" + remotePeerId + "' LOGIN REQUEST received: LOGGED IN!.");
                        } else {
                            LOG.e("PeerHandler.handleMessage() for peer '" + remotePeerId + "' LOGIN REQUEST received: FORBIDDEN for him!");
                            serverForCallback.markDeadPeerHandler(serverStrangeId);
                            spaceForCallback.markDisconnectedForOUT(remotePeerId);
                            go = false;
                        }
                    } else if (payload != null) {
                        // LOGIN ANSWER
                        if (payload.equals("OK")) {
                            // i'am logged to other's server
                            LOG.o("PeerHandler.handleMessage() for peer '" + remotePeerId + "' LOGIN REQUEST received: LOGGED IN!.");
                        } else {
                            // nope
                            LOG.o("PeerHandler.handleMessage() for peer '" + remotePeerId + "' LOGIN REQUEST received: LOGGED IN!.");
                        }
                    } else {
                        // security breach/issue/error
                        LOG.e("PeerHandler.handleMessage() for peer '" + remotePeerId + "' Security/Encryption error (" + kind + ")!");
                        serverForCallback.markDeadPeerHandler(serverStrangeId);
                        spaceForCallback.markDisconnectedForOUT(remotePeerId);
                        go = false;
                    }
                }
                break;
            case EncryptedMessageObject.KIND_META:
                String decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());

                if (decrypted != null) {
                    EJSONObject meta = new EJSONObject(decrypted);
                    LOG.i("PeerHandler.handleMessage() for peer '" + remotePeerId + "', go for listen_meta().");
                    spaceForCallback.listen_meta(m.getFromPeer(), meta);
                } else {
                    // security breach/issue/error
                    LOG.e("PeerHandler.handleMessage() for peer '" + remotePeerId + "' Security/Encryption error (" + kind + ")!");
                    serverForCallback.markDeadPeerHandler(serverStrangeId);
                    spaceForCallback.markDisconnectedForOUT(remotePeerId);
                    go = false;
                }
                break;
            case EncryptedMessageObject.KIND_ERROR:
                decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                if (decrypted != null) {
                    EJSONObject error = new EJSONObject(decrypted);
                    LOG.i("PeerHandler.handleMessage() for peer '" + remotePeerId + "', go for listen_error().");
                    spaceForCallback.listen_error(m.getFromPeer(), error);
                } else {
                    // security breach/issue/error
                    LOG.e("PeerHandler.handleMessage() for peer '" + remotePeerId + "' Security/Encryption error (" + kind + ")!");
                    serverForCallback.markDeadPeerHandler(serverStrangeId);
                    spaceForCallback.markDisconnectedForOUT(remotePeerId);
                    go = false;
                }
                break;
            case EncryptedMessageObject.KIND_MOBJECT:
                decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                if (decrypted != null) {
                    EJSONArray arr = new EJSONArray(decrypted);
                    LOG.i("PeerHandler.handleMessage() for peer '" + remotePeerId + "', go for listen_parseAndWorkoutListenedObjects().");
                    spaceForCallback.listen_parseAndWorkoutListenedObjects(m.getFromPeer(), arr);
                } else {
                    // security breach/issue/error
                    LOG.e("PeerHandler.handleMessage() for peer '" + remotePeerId + "' Security/Encryption error (" + kind + ")!");
                    serverForCallback.markDeadPeerHandler(serverStrangeId);
                    spaceForCallback.markDisconnectedForOUT(remotePeerId);
                    go = false;
                }
                break;
            default: // object
                decrypted = spaceForCallback.decrypt(remotePeerId, m.getPayload());
                if (decrypted != null) {
                    EJSONObject json = new EJSONObject(decrypted);
                    LOG.i("PeerHandler.handleMessage() for peer '" + remotePeerId + "', go for listen_parseAndWorkoutListenedObject().");
                    spaceForCallback.listen_parseAndWorkoutListenedObject(m.getFromPeer(), json);
                } else {
                    // security breach/issue/error
                    LOG.e("PeerHandler.handleMessage() for peer '" + remotePeerId + "' Security/Encryption error (" + kind + ")!");
                    serverForCallback.markDeadPeerHandler(serverStrangeId);
                    spaceForCallback.markDisconnectedForOUT(remotePeerId);
                    go = false;
                }
                break;
        }
    }

}
