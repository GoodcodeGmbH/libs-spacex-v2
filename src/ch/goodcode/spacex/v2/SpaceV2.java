/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2;

import ch.goodcode.libs.logging.LogBuffer;
import ch.goodcode.libs.io.EnhancedFilesystemIO;
import ch.goodcode.libs.io.serialization.Java2JSONSerializer;
import ch.goodcode.libs.security.CryptoEngine;
import ch.goodcode.libs.security.EnhancedCryptography;
import ch.goodcode.libs.security.PermissionEngine;
import ch.goodcode.libs.threading.ThreadManager;
import ch.goodcode.libs.utils.GOOUtils;
import ch.goodcode.libs.utils.ReflectUtils;
import ch.goodcode.libs.utils.dataspecs.EJSONArray;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import ch.goodcode.spacex.v2.engine.AckEntry;
import ch.goodcode.spacex.v2.engine.DeleteEntry;
import ch.goodcode.spacex.v2.engine.DeleteMessage;
import ch.goodcode.spacex.v2.engine.EncryptedMessageObject;
import ch.goodcode.spacex.v2.engine.MiniClient;
import ch.goodcode.spacex.v2.engine.MiniServer;
import ch.goodcode.spacex.v2.engine.RegVar;
import ch.goodcode.spacex.v2.engine.TokensPolicy;
import ch.goodcode.spacex.v2.engine.UpdateEntry;
import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKey;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

/**
 *
 * @author Paolo Domenighetti
 */
public final class SpaceV2 {

    // ======================================================================================================================================
    private static final ArrayList<String> INNER_CLAZZES_NAMES = new ArrayList<>();

    static {
        INNER_CLAZZES_NAMES.add(UpdateEntry.class.getName());
        INNER_CLAZZES_NAMES.add(DeleteEntry.class.getName());
        INNER_CLAZZES_NAMES.add(AckEntry.class.getName());

    }

    private static final String DEFAULT_CRYPTO_KEY_ID = "DEFAULT";
    private static final String DEFAULT_ISO_KEY = "DgVC7DaGMq9+fwnt+bjaZg==";
    private static final int EMF_SIZE_LIMIT = 1_000_000;
    private static final int EM_PURGE_LIMIT = 50;
    private static final long EM_PURGE_TIMEOUT = 20 * GOOUtils.TIME_MINUTES;
    private static final int MAGIC_BATCH_BOUNDARY = 50;
    private static final int MAGIC_BATCH_DIVISOR = 20_000;

    //-
    private final boolean IS_REAL_SPACE;
    private final String myId;
    private final String myPassword;

    public String geSpaceId() {
        return myId;
    }

    //-
    private LogBuffer LOG;
    private EntityManagerFactory mainEMF;
    private final HashMap<String, EntityManagerFactory> emfsMAP = new HashMap<>();
    private final HashMap<String, TokensPolicy> tokensPolicies = new HashMap<>();
    private final HashMap<Long, EntityManager> ems = new HashMap<>();
    private final HashMap<Long, Long> emsT = new HashMap<>();
    private long emsC = 0L;
    private final EJSONObject odbConf;
    private final String optHostFull;
    private String optUser, optPass;
    private final ThreadManager tmanager = new ThreadManager(100, 100);

    // -
    private final EJSONObject spaceConf;
    private MiniServer server;
    private SpacePeer[] peers;
    private final HashMap<String, MiniClient> clients = new HashMap<>();
    private long mcounter = 0L;

    // -
    private final CryptoEngine spaceCryptos = new CryptoEngine();
    private final PermissionEngine spacePermissions = new PermissionEngine();

    /**
     *
     * @param uid
     * @param spacePassword may be null if there is no REAL SPACE
     * @param logPath
     * @param logLevel
     * @param odbConfFilePath
     * @param spaceConfFilePath
     */
    public SpaceV2(String uid, String spacePassword, String logPath, int logLevel, String odbConfFilePath, String spaceConfFilePath) {
        LOG = new LogBuffer("spacev2-" + uid, logPath, 1000, logLevel);
        IS_REAL_SPACE = spaceConfFilePath != null;
        odbConf = new EJSONObject(EnhancedFilesystemIO.fileRead(new File(odbConfFilePath)).toString());
        if (IS_REAL_SPACE) {
            spaceConf = new EJSONObject(EnhancedFilesystemIO.fileRead(new File(spaceConfFilePath)).toString());
        } else {
            spaceConf = null;
        }
        optHostFull = null;
        this.myId = uid;
        this.myPassword = spacePassword;
    }

    /**
     * For DEBUG Constructor
     *
     * @param uid
     * @param logPath
     * @param logLevel
     * @param hostStringFull
     * @param user
     * @param pass
     */
    public SpaceV2(String uid, String logPath, int logLevel, String hostStringFull, String user, String pass) {
        LOG = new LogBuffer("spacev2-" + uid, logPath, 1000, logLevel);
        IS_REAL_SPACE = false;
        odbConf = null;
        spaceConf = null;
        optHostFull = hostStringFull;
        optUser = user;
        optPass = pass;
        this.myId = uid;
        this.myPassword = "1234";
    }

    /**
     * For DEBUG Constructor
     *
     * @param uid
     * @param logPath
     * @param logLevel
     * @param odbFilePath
     */
    public SpaceV2(String uid, String logPath, int logLevel, String odbFilePath) {
        LOG = new LogBuffer("spacev2-" + uid, logPath, 1, logLevel);
        IS_REAL_SPACE = false;
        odbConf = null;
        spaceConf = null;
        optHostFull = odbFilePath;
        this.myId = uid;
        this.myPassword = "1234";
    }

    /**
     * For DEBUG Constructor
     *
     * @param uid
     * @param logPath
     * @param logLevel
     * @param debugId
     */
    public SpaceV2(String uid, String logPath, int logLevel, long debugId) {
        this(uid, logPath, logLevel, "$objectdb/db/debug_" + debugId + ".odb");
    }

    /**
     *
     * @throws Exception
     */
    public void start() throws Exception {
        LOG.o("Starting SV2 instance '" + myId + "'...");

        // put in place MEM TIER ---------------------------------------------------
        if (optUser == null) {

            mainEMF = Persistence.createEntityManagerFactory(
                    optHostFull);

            LOG.o("Mod0 (debug) detected: main ef only with path " + optHostFull);

        } else if (odbConf == null) {

            Map<String, String> properties = new HashMap<>();
            properties.put("javax.persistence.jdbc.user", optUser);
            properties.put("javax.persistence.jdbc.password", optPass);
            mainEMF = Persistence.createEntityManagerFactory(
                    optHostFull,
                    properties);

            LOG.o("Mod1 ('configless', debug) detected: main ef only with path " + optHostFull + " and user access");

        } else {

            LOG.o("odb Config file detected and properly parsed.");

            EJSONObject mainUnitJson = odbConf.getObject("mainUnit");
            if (mainUnitJson.getBoolean("isFile")) {
                mainEMF = Persistence.createEntityManagerFactory(
                        mainUnitJson.getString("memPath"));
            } else {
                Map<String, String> properties = new HashMap<>();
                properties.put("javax.persistence.jdbc.user", mainUnitJson.getString("odbUser"));
                properties.put("javax.persistence.jdbc.password", mainUnitJson.getString("odbPass"));
                mainEMF = Persistence.createEntityManagerFactory(
                        "objectdb://" + mainUnitJson.getString("odbHost") + ":" + mainUnitJson.getInteger("odbPort") + "/" + mainUnitJson.getString("memUid") + ".odb",
                        properties);

                EJSONObject backup = mainUnitJson.getObject("backup");
                if (backup != null) {
                    String target = backup.getString("target");
                    tmanager.fetchDaemon(() -> {
                        Query backupQuery = mainEMF.createEntityManager().createQuery("objectdb backup");
                        backupQuery.setParameter("target", new java.io.File(target));
                        backupQuery.getSingleResult();
                    }, 5000L, backup.getInteger("schedule") * GOOUtils.TIME_HOURS);
                }
            }

            EJSONArray otherUnitsJsonArray = odbConf.getArray("otherUnits");
            for (int i = 0; i < otherUnitsJsonArray.size(); i++) {
                EJSONObject unitJson = otherUnitsJsonArray.getObject(i);
                if (unitJson.getBoolean("isFile")) {
                    if (unitJson.getBoolean("isTokenized")) {

                        String dt = unitJson.getString("dataType");
                        int tokens = (unitJson.getInteger("dataSize") / EMF_SIZE_LIMIT) + 1;
                        tokensPolicies.put(dt, new TokensPolicy(tokens, unitJson.getString("tokensTypeRule"), unitJson.getString("tokensField")));

                        for (int j = 0; j < tokens; j++) {
                            EntityManagerFactory anEmf = Persistence.createEntityManagerFactory(
                                    unitJson.getString("memSubfolder") + "/" + dt.toLowerCase() + "_" + j + ".odb");
                            emfsMAP.put(dt + "_" + j, anEmf);
                            EJSONObject backup = unitJson.getObject("backup");
                            if (backup != null) {
                                String target = backup.getString("target");
                                tmanager.fetchDaemon(() -> {
                                    Query backupQuery = anEmf.createEntityManager().createQuery("objectdb backup");
                                    backupQuery.setParameter("target", new java.io.File(target));
                                    backupQuery.getSingleResult();
                                }, 5000L, backup.getInteger("schedule") * GOOUtils.TIME_HOURS);

                            }
                        }

                    } else {
                        EntityManagerFactory anEmf = Persistence.createEntityManagerFactory(
                                unitJson.getString("memPath"));
                        EJSONArray types = unitJson.getArray("dataTypes");
                        for (int j = 0; j < types.size(); j++) {
                            emfsMAP.put(types.getString(j), anEmf);
                        }

                        EJSONObject backup = unitJson.getObject("backup");
                        if (backup != null) {
                            String target = backup.getString("target");
                            tmanager.fetchDaemon(() -> {
                                Query backupQuery = anEmf.createEntityManager().createQuery("objectdb backup");
                                backupQuery.setParameter("target", new java.io.File(target));
                                backupQuery.getSingleResult();
                            }, 5000L, backup.getInteger("schedule") * GOOUtils.TIME_HOURS);

                        }

                    }

                } else {
                    Map<String, String> properties = new HashMap<>();
                    properties.put("javax.persistence.jdbc.user", unitJson.getString("odbUser"));
                    properties.put("javax.persistence.jdbc.password", unitJson.getString("odbPass"));
                    EntityManagerFactory anEmf = Persistence.createEntityManagerFactory(
                            "objectdb://" + unitJson.getString("odbHost") + ":" + unitJson.getInteger("odbPort") + "/" + unitJson.getString("memUid") + ".odb",
                            properties);
                    EJSONArray types = unitJson.getArray("dataTypes");
                    for (int j = 0; j < types.size(); j++) {
                        emfsMAP.put(types.getString(j), anEmf);
                    }
                }
            }

            LOG.o("ModX detected: main emf only with path " + optHostFull + ", emfs built: " + emfsMAP.size());

        }

        // put in place COM/P2P TIER --------------------------------------------------------
        if (IS_REAL_SPACE) {
            LOG.o("SV2 Space enabled, deploying and starting it on port " + spaceConf.getInteger("listeningPort") + "...");
            // prepare the main listening server for announces and objects:
            server = new MiniServer(this, spaceConf.getInteger("listeningPort"), LOG);
            server.startInThread();
            for (SpacePeer peer : peers) {
                // for every registered peer prepare the com client to send messages (when they will be online):
                MiniClient c = new MiniClient(this, peer.getHost(), peer.getPort(), peer.getUid(), LOG);
                c.start();
                clients.put(peer.getUid(), c);
            }

            // TODO init permissions and cryptos!!!!
            // save the default key:
            spaceCryptos.createAndStoreKey(DEFAULT_CRYPTO_KEY_ID, DEFAULT_ISO_KEY);

            //..
            //..
            // space updater
            for (SpacePeer peer : peers) {
                tmanager.fetchDaemon(() -> {
                    if (peer.isConnected()) {
                        ArrayList<UpdateEntry> tbdel = new ArrayList<>();
                        ArrayList<IHyperspaceEntity> retrieveToBeUpdatedEntities = retrieveToBeUpdatedEntities(peer.getUid(), tbdel);
                        boolean ok = sendObjectsToPeer(peer.getUid(), retrieveToBeUpdatedEntities);
                        if (ok) {

                        }
                    }

                },
                        GOOUtils.TIME_SECONDS,
                        20 * GOOUtils.TIME_SECONDS);
            }

            // space deleter
            for (SpacePeer peer : peers) {
                tmanager.fetchDaemon(() -> {
                    if (peer.isConnected()) {
                        ArrayList<DeleteEntry> tbdel = new ArrayList<>();
                        List<DeleteMessage> sueToBeDeletedMessages = issueToBeDeletedMessages(peer.getUid(), tbdel);
                    }

                },
                        GOOUtils.TIME_SECONDS,
                        20 * GOOUtils.TIME_SECONDS);
            }

            LOG.o("SV2 Space is ready.");
        }

        if (IS_REAL_SPACE) {
            LOG.o("SV2 full instance '" + myId + " with its Space (for " + peers.length + " registered peers) has properly started. Have a nice day.");
        } else {
            LOG.o("SV2 instance '" + myId + " has properly started. Have a nice day.");
        }
    }

    private final HashMap<String, ILevel3Updatable<?>> LEVEL3CACHELISTENERS = new HashMap<>();

    /**
     *
     * @param <T>
     * @param clazz
     * @param aLevel3CacheListener
     */
    public <T> void registerL3CacheListener(Class<T> clazz, ILevel3Updatable<T> aLevel3CacheListener) {
        LEVEL3CACHELISTENERS.put(clazz.getSimpleName(), aLevel3CacheListener);
    }

    private <T> void createForCacheListeners(T object) {
        final String name = object.getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ((ILevel3Updatable<T>) LEVEL3CACHELISTENERS.get(name)).create(object);
        }
    }

    private <T> void updateForCacheListeners(T object) {
        final String name = object.getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ((ILevel3Updatable<T>) LEVEL3CACHELISTENERS.get(name)).update(object);
        }
    }

    private <T> void deleteForCacheListeners(T object) {
        final String name = object.getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ((ILevel3Updatable<T>) LEVEL3CACHELISTENERS.get(name)).delete(object);
        }
    }

    private <T> void createForCacheListeners(List<T> objects) {
        final String name = objects.get(0).getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3Updatable<T> c = ((ILevel3Updatable<T>) LEVEL3CACHELISTENERS.get(name));
            c.create(objects);
        }
    }

    private <T> void updateForCacheListeners(List<T> objects) {
        final String name = objects.get(0).getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3Updatable<T> c = ((ILevel3Updatable<T>) LEVEL3CACHELISTENERS.get(name));
            c.update(objects);
        }
    }

    private <T> void deleteForCacheListeners(List<T> objects) {
        final String name = objects.get(0).getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3Updatable<T> c = ((ILevel3Updatable<T>) LEVEL3CACHELISTENERS.get(name));
            c.delete(objects);
        }
    }

    private String mid(String forgeMethodName, long time) {
        mcounter++;
        return GOOUtils.toHashcode_SHA256(forgeMethodName + time + mcounter);
    }

    public EncryptedMessageObject issueMessage_ANNOUNCE_request(String otherPeerId) {
        long t = System.currentTimeMillis();
        EncryptedMessageObject o = new EncryptedMessageObject(
                mid("issueMessage_ANNOUNCE", t),
                EncryptedMessageObject.KIND_ACCESS,
                encrypt(null, otherPeerId), // the announce is default encrypted
                myId,
                t
        );
        return o;
    }

    public EncryptedMessageObject issueMessage_ANNOUNCE_answer(String newGenKey) {
        long t = System.currentTimeMillis();
        EncryptedMessageObject o = new EncryptedMessageObject(
                mid("issueMessage_ANNOUNCE_answer", t),
                EncryptedMessageObject.KIND_ACCESS,
                encrypt(null, "KEYED:" + newGenKey), // the announce is default encrypted
                myId,
                t
        );
        return o;
    }

    public EncryptedMessageObject issueMessage_LOGIN_request(String otherPeerId) {
        long t = System.currentTimeMillis();
        EncryptedMessageObject o = new EncryptedMessageObject(
                mid("issueMessage_LOGIN", t),
                EncryptedMessageObject.KIND_ACCESS,
                encrypt(otherPeerId, myId + ":" + myPassword),
                myId,
                t
        );
        return o;
    }

    public EncryptedMessageObject issueMessage_LOGIN_answer(String otherPeerId, boolean ans) {
        long t = System.currentTimeMillis();
        if (ans) {
            EncryptedMessageObject o = new EncryptedMessageObject(
                    mid("issueMessage_ANNOUNCE_answer", t),
                    EncryptedMessageObject.KIND_ACCESS,
                    encrypt(otherPeerId, "OK"),
                    myId,
                    t
            );
            return o;
        } else {
            EncryptedMessageObject o = new EncryptedMessageObject(
                    mid("issueMessage_ANNOUNCE_answer", t),
                    EncryptedMessageObject.KIND_ACCESS,
                    encrypt(otherPeerId, "ERROR"),
                    myId,
                    t
            );
            return o;
        }
    }

    public boolean listen_login(String peerId, String loginStringPayload) {
        String[] split = loginStringPayload.split(":");
        String u = split[0];
        String p = split[1];
        if (u.equals(peerId)) {
            return spacePermissions.login(u, p);
        } else {
            return false;
        }
    }

    public void markConnectedForOUT(String peerId) {
        for (SpacePeer peer : peers) {
            if (peer.getUid().equals(peerId)) {
                peer.setConnected(true);
                break;
            }
        }
    }

    public void markDisconnectedForOUT(String peerId) {
        for (SpacePeer peer : peers) {
            if (peer.getUid().equals(peerId)) {
                peer.setConnected(false);
                break;
            }
        }
    }

    public void listen_meta(String peerId, EJSONObject meta) {
        // they are either delete messages or ack messages

    }

    public void listen_error(String peerId, EJSONObject error) {

    }

    public void listen_parseAndWorkoutListenedObjects(String peerId, EJSONArray payload) {
        // must be fast!
        HashMap<String, ArrayList<EJSONObject>> map = new HashMap<>();
        try {
            for (int i = 0; i < payload.size(); i++) {
                EJSONObject object = payload.getObject(i);
                String cln = object.getString("clazz");
                if(!map.containsKey(cln)) {
                    map.put(cln, new ArrayList<>());
                }
                map.get(cln).add(object);
            }
            
            for (Map.Entry<String, ArrayList<EJSONObject>> entry : map.entrySet()) {
                Class<?> aClass = ReflectUtils.getClass(entry.getKey());
                ArrayList<Object> res = new ArrayList<>();
                ArrayList<EJSONObject> value = entry.getValue();
                for (EJSONObject eJSONObject : value) {
                    res.add(deser(eJSONObject, aClass));
                }
                update(res); // <<------------------------------------------- update/create
            }
            
        } catch (Exception ex) {

        }

    }

    public void listen_parseAndWorkoutListenedObject(String peerId, EJSONObject payload) {
        try {
            // must be fast!
            Class<?> aClass = ReflectUtils.getClass(payload.getString("clazz"));
            Object deser = deser(payload, aClass);
            update(deser); // <<------------------------------------------- update/create
        } catch (Exception ex) {
            
        }
    }

    public String issueAndRegisterSessionKeyForPeer(String peerId) {
        // IN keys
        SecretKey k;
        try {
            k = EnhancedCryptography.generateSecretKey();
            String saveSecretKey = EnhancedCryptography.saveSecretKey(k);
            spaceCryptos.createAndStoreKey(peerId + ":IN", saveSecretKey);
            return saveSecretKey;
        } catch (NoSuchAlgorithmException | NoSuchProviderException ex) {
            return null; // always works, no problem
        }

    }

    public boolean saveAndRegisterSessionKeyForPeer(String peerId, String k) {
        // OUT keys
        for (int i = 0; i < peers.length; i++) {
            SpacePeer peer = peers[i];
            if (peer.getUid().equals(peerId)) {
                spaceCryptos.createAndStoreKey(peerId + ":OUT", k);
                return true;
            }
        }
        return false;
    }

    public void sendMessageToPeer(String peerId, EncryptedMessageObject m) {

    }

    public boolean sendDeleteMessageToPeer(String peerId, List<DeleteMessage> msgs) {
        return false;
    }

    public boolean sendObjectsToPeer(String peerId, List<IHyperspaceEntity> objects) {
        long t = System.currentTimeMillis();
        EJSONArray a = new EJSONArray();
        for (IHyperspaceEntity object : objects) {
            a.addObject(ser(object));
        }
        EncryptedMessageObject o = new EncryptedMessageObject(
                mid("sendObjectsToPeer", t),
                EncryptedMessageObject.KIND_MOBJECT,
                encrypt(peerId, a.toJSONString()),
                myId,
                t
        );
        return clients.get(peerId).sendMessage(o);
    }

    public String encrypt(String keyID, String s) {
        if (keyID == null) {
            keyID = DEFAULT_CRYPTO_KEY_ID;
        } else if (!keyID.equals(DEFAULT_CRYPTO_KEY_ID)) {
            keyID = keyID + ":OUT"; // this is the session key received from the other peer
        }
        return spaceCryptos.decryptInline(keyID, s);
    }

    public String decrypt(String keyID, String s) {
        if (keyID == null) {
            keyID = DEFAULT_CRYPTO_KEY_ID;
        } else if (!keyID.equals(DEFAULT_CRYPTO_KEY_ID)) {
            keyID = keyID + ":IN"; // this is my generated session key for the peer
        }
        return spaceCryptos.encryptInline(keyID, s);
    }

    /**
     *
     * @throws Exception
     */
    public void stop() throws Exception {
        LOG.o("Now stopping SV2 '" + myId + "'...");
        if (IS_REAL_SPACE) {

        }

        for (Map.Entry<Long, EntityManager> entry : ems.entrySet()) {
            EntityManager em = entry.getValue();
            em.close();
        }
        ems.clear();
        for (Map.Entry<String, EntityManagerFactory> entry : emfsMAP.entrySet()) {
            entry.getValue().close();
        }
        emfsMAP.clear();

        //-
        mainEMF.close();
        LOG.o("Stopped SV2 '" + myId + "', goodbye.");
        LOG.s();
    }

    private <T> EntityManager em(Thread currentThread, Class<T> clazz) {
        if (tokensPolicies.containsKey(clazz.getSimpleName())) {
            return null; // <<-- in token processing...
        } else {
            if (emsC > EM_PURGE_LIMIT) {
                emsC = 0L;
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long now = System.currentTimeMillis();
                        ArrayList<Long> tbDelTids = new ArrayList<>();
                        for (Map.Entry<Long, Long> entry : emsT.entrySet()) {
                            Long tid = entry.getKey();
                            Long lasrtSeen = entry.getValue();
                            if (now - lasrtSeen > EM_PURGE_TIMEOUT) {
                                tbDelTids.add(tid);
                            }
                        }
                        for (Long threadID : tbDelTids) {
                            ems.remove(threadID);
                            emsT.remove(threadID);
                        }
                    }
                })).start();
            }
            if (!ems.containsKey(currentThread.getId())) {
                if (emfsMAP.containsKey(clazz.getSimpleName())) {
                    ems.put(currentThread.getId(), emfsMAP.get(clazz.getSimpleName()).createEntityManager());
                } else {
                    ems.put(currentThread.getId(), mainEMF.createEntityManager());
                }
                emsC++;
            }
            emsT.put(currentThread.getId(), System.currentTimeMillis());
            return ems.get(currentThread.getId());
        }
    }

    private <T> boolean isNotInnerType(T item) {
        return !INNER_CLAZZES_NAMES.contains(item.getClass().getName());
    }

    private <T> boolean isNotInnerType(List<T> items) {
        return isNotInnerType(items.get(0));
    }

    private <T> void markForSpace_CREATE(T item) {
        markForSpace_UPDATE(item);
    }

    private <T> void markForSpace_UPDATE(T item) {
        if (item instanceof IHyperspaceEntity) {
            for (SpacePeer peer : peers) {
                UpdateEntry e = new UpdateEntry();
                e.setClazzname(item.getClass().getSimpleName());
                e.setLastUpdated(System.currentTimeMillis());
                e.setPeer(peer.getUid());
                e.setTarget(((IHyperspaceEntity) item).uid());
                create(e);
            }
        }
    }

    private <T> void markForSpace_DELETE(T item) {
        if (item instanceof IHyperspaceEntity) {
            for (SpacePeer peer : peers) {
                DeleteEntry e = new DeleteEntry();
                e.setClazzname(item.getClass().getSimpleName());
                e.setLastDeleted(System.currentTimeMillis());
                e.setPeer(peer.getUid());
                e.setTarget(((IHyperspaceEntity) item).uid());
                create(e);
            }
        }
    }

    private <T> void markForSpace_CREATE(List<T> items) {
        markForSpace_UPDATE(items);
    }

    private <T> void markForSpace_UPDATE(List<T> items) {
        if (items.get(0) instanceof IHyperspaceEntity) {
            for (T item : items) {
                for (SpacePeer peer : peers) {
                    UpdateEntry e = new UpdateEntry();
                    e.setClazzname(item.getClass().getSimpleName());
                    e.setLastUpdated(System.currentTimeMillis());
                    e.setPeer(peer.getUid());
                    e.setTarget(((IHyperspaceEntity) item).uid());
                    create(e);
                }
            }
        }
    }

    private <T> void markForSpace_DELETE(List<T> items) {
        if (items.get(0) instanceof IHyperspaceEntity) {
            for (T item : items) {
                for (SpacePeer peer : peers) {
                    DeleteEntry e = new DeleteEntry();
                    e.setClazzname(item.getClass().getSimpleName());
                    e.setLastDeleted(System.currentTimeMillis());
                    e.setPeer(peer.getUid());
                    e.setTarget(((IHyperspaceEntity) item).uid());
                    create(e);
                }
            }
        }
    }

    // ========================================================================
    // Tokens utility methods
    // Tokens mod is almost not used, in general we work on the model classes
    // to tokenize the dataset eg. EBook -> EBook_A, EBook_B, etc...; ten types limit is
    // overcome defining multiple simple non tokenized otherUnit(s) and adapting the a ModelController
    // to handle multiple classes as if it was one!
    // (they very well may use the public api of course for meta types entities)
    // ========================================================================
    // Space utility methods
    // There should be here some kinf of Locking TODO
    // (they very well may use the public api of course for meta types entities)
    // ========================================================================
    // PUBLIC API C(R)UD
    // =====================
    /**
     *
     * ---------------------------------------------------------------- General
     * behavior for persistency are the following: 1) the proper thread-related
     * entity manager is fetched 2) JPA crud transaction executed 2a) if NOT ok,
     * then rollback transaction, warn and keep going without any other JPA
     * invokations; 2b) else apply JPA crud action and finally: - check for
     * hyperspace mod, if yes apply screate(...) - ( may be asynch) see ref. -
     * apply createForCacheListeners(...) -(may be asynch) see ref.
     *
     * @param <T>
     * @param item
     */
    public <T> void create(T item) {
        EntityManager em = em(Thread.currentThread(), item.getClass());
        try {
            if (em != null) {
                em.getTransaction().begin();
                em.persist(item);
                em.getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in create()");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in create()", e);
        } finally {
            if (em != null && em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            } else if (em != null) {
                if (IS_REAL_SPACE && isNotInnerType(item)) {
                    // -
                    markForSpace_CREATE(item);
                }
                createForCacheListeners(item);
            }
        }
    }

    /**
     *
     * @param <T>
     * @param item
     */
    public <T> void update(T item) {
        EntityManager em = em(Thread.currentThread(), item.getClass());
        try {
            if (em != null) {
                em.getTransaction().begin();
                em.merge(item);
                em.getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in update()");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in update()", e);
        } finally {
            if (em != null && em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            } else if (em != null) {
                if (IS_REAL_SPACE && isNotInnerType(item)) {
                    // -
                    markForSpace_UPDATE(item);
                }
                updateForCacheListeners(item);
            }
        }
    }

    /**
     *
     * @param <T>
     * @param item
     */
    public <T> void delete(T item) {
        EntityManager em = em(Thread.currentThread(), item.getClass());
        try {
            if (em != null) {
                em.getTransaction().begin();
                em.remove(item);
                em.getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in delete()");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in delete()", e);
        } finally {
            if (em != null && em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            } else if (em != null) {
                if (IS_REAL_SPACE && isNotInnerType(item)) {
                    // -
                    markForSpace_DELETE(item);
                }
                deleteForCacheListeners(item);
            }
        }
    }

    /**
     *
     * @param <T>
     * @param items
     */
    public <T> void create(List<T> items) {
        int size = items.size();
        if (size < MAGIC_BATCH_BOUNDARY) {
            for (T it : items) {
                create(it);
            }
        } else {
            EntityManager em = em(Thread.currentThread(), items.get(0).getClass());
            try {
                if (em != null) {
                    em.getTransaction().begin();
                    int i = 0;
                    for (T it : items) {
                        em.persist(it);
                        if ((i % MAGIC_BATCH_DIVISOR) == 0) {
                            em.getTransaction().commit();
                            em.clear();
                            em.getTransaction().begin();
                        }
                        i++;
                    }
                    em.getTransaction().commit();
                } else {
                    LOG.e("Null Entity manager in create(List<>)");
                }
            } catch (Exception e) {
                LOG.e("Transaction exception in create(List<>)", e);
            } finally {
                if (em != null && em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                } else if (em != null) {
                    if (IS_REAL_SPACE && isNotInnerType(items)) {
                        //-
                        markForSpace_CREATE(items);
                    }
                    createForCacheListeners(items);
                }
            }

        }
    }

    /**
     *
     * @param <T>
     * @param items
     */
    public <T> void update(List<T> items) {
        int size = items.size();
        if (size < MAGIC_BATCH_BOUNDARY) {
            for (T it : items) {
                update(it);
            }
        } else {
            EntityManager em = em(Thread.currentThread(), items.get(0).getClass());

            try {
                if (em != null) {
                    em.getTransaction().begin();
                    int i = 0;
                    for (T it : items) {
                        em.merge(it);
                        if ((i % MAGIC_BATCH_DIVISOR) == 0) {
                            em.getTransaction().commit();
                            em.clear();
                            em.getTransaction().begin();
                        }
                        i++;
                    }
                    em.getTransaction().commit();
                } else {
                    LOG.e("Null Entity manager in update(List<>)");
                }
            } catch (Exception e) {
                LOG.e("Transaction exception in update(List<>)", e);
            } finally {
                if (em != null && em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                } else if (em != null) {
                    if (IS_REAL_SPACE && isNotInnerType(items)) {
                        // -
                        markForSpace_UPDATE(items);
                    }
                    updateForCacheListeners(items);
                }
            }

        }
    }

    /**
     *
     * @param <T>
     * @param items
     */
    public <T> void delete(List<T> items) {
        int size = items.size();
        if (size < MAGIC_BATCH_BOUNDARY) {
            for (T it : items) {
                delete(it);
            }
        } else {
            EntityManager em = em(Thread.currentThread(), items.get(0).getClass());
            try {
                if (em != null) {
                    em.getTransaction().begin();
                    int i = 0;
                    for (T it : items) {
                        em.remove(it);
                        if ((i % MAGIC_BATCH_DIVISOR) == 0) {
                            em.getTransaction().commit();
                            em.clear();
                            em.getTransaction().begin();
                        }
                        i++;
                    }
                    em.getTransaction().commit();
                } else {
                    LOG.e("Null Entity manager in delete(List<>)");
                }
            } catch (Exception e) {
                LOG.e("Transaction exception in delete(List<>)", e);
            } finally {
                if (em != null && em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                } else if (em != null) {
                    if (IS_REAL_SPACE && isNotInnerType(items)) {
                        // -
                        markForSpace_DELETE(items);
                    }
                    deleteForCacheListeners(items);
                }
            }

        }
    }

    // ========================================================================
    // PUBLIC API Retrieval (BASE)
    // =====================
    /**
     *
     * @param key
     * @return
     */
    public String readRegVar(String key) {
        if (key != null) {
            RegVar v = get(RegVar.class, key);
            if (v != null) {
                return v.getValue();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     *
     * @param key
     * @param value
     */
    public void writeregVar(String key, String value) {
        if (key != null) {
            RegVar get = get(RegVar.class, key);
            if (get != null) {
                get.setValue(value);
                update(get);
            } else {
                RegVar v = new RegVar();
                v.setKey(key);
                v.setValue(value);
                create(v);
            }
        }
    }

    // ..
    /**
     *
     * @param <T>
     * @param clazz
     * @param orderByClause
     * @return
     */
    public <T> List<T> getAll(Class<T> clazz, String orderByClause) {
        TypedQuery<T> query = em(Thread.currentThread(), clazz).createQuery(
                "SELECT c FROM Object c WHERE c instanceof " + clazz.getSimpleName(),
                clazz);
        return query.getResultList();
    }

    /**
     *
     * Note: this is also backward compatible with SpaceX or, if missing, with
     * the same set as getSharable(clazz, String)
     *
     * @param <T>
     * @param clazz
     * @param someStringUidJPAViaAnnotationsDescribed
     * @return
     */
    public <T> T get(Class<T> clazz, String someStringUidJPAViaAnnotationsDescribed) {
        EntityManager em = em(Thread.currentThread(), clazz);
        T fullObject = em.find(clazz, someStringUidJPAViaAnnotationsDescribed);
        return fullObject;
    }

    /**
     *
     * Warning: the returned object may be HOLLOW, i.e. not fully initialized.
     * Note: use this if you need an entity only to build a reference to it in
     * some other entities.
     *
     * @param <T>
     * @param clazz
     * @param someStringUidJPAViaAnnotationsDescribed
     * @return
     */
    public <T> T point(Class<T> clazz, String someStringUidJPAViaAnnotationsDescribed) {
        EntityManager em = em(Thread.currentThread(), clazz);
        T mayBeHollow = em.getReference(clazz, someStringUidJPAViaAnnotationsDescribed);
        return mayBeHollow;
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @param someLongIdJPAViaAnnotationsDescribed
     * @return
     */
    public <T> T get(Class<T> clazz, long someLongIdJPAViaAnnotationsDescribed) {
        EntityManager em = em(Thread.currentThread(), clazz);
        T fullObject = em.find(clazz, someLongIdJPAViaAnnotationsDescribed);
        return fullObject;
    }

    /**
     * Warning: the returned object may be HOLLOW, i.e. not fully initialized.
     * Note: use this if you need an entity only to build a reference to it in
     * some other entities.
     *
     * @param <T>
     * @param clazz
     * @param someLongIdJPAViaAnnotationsDescribed
     * @return
     */
    public <T> T point(Class<T> clazz, long someLongIdJPAViaAnnotationsDescribed) {
        EntityManager em = em(Thread.currentThread(), clazz);
        T mayBeHollow = em.getReference(clazz, someLongIdJPAViaAnnotationsDescribed);
        return mayBeHollow;
    }

    /**
     * Note: this is also backward compatible with both SpaceX and HibProject
     *
     * @param <T>
     * @param clazz
     * @param someIntIdJPAViaAnnotationsDescribed
     * @return
     */
    public <T> T get(Class<T> clazz, int someIntIdJPAViaAnnotationsDescribed) {
        EntityManager em = em(Thread.currentThread(), clazz);
        T fullObject = em.find(clazz, someIntIdJPAViaAnnotationsDescribed);
        return fullObject;
    }

    /**
     * Warning: the returned object may be HOLLOW, i.e. not fully initialized.
     * Note: use this if you need an entity only to build a reference to it in
     * some other entities.
     *
     * @param <T>
     * @param clazz
     * @param someIntIdJPAViaAnnotationsDescribed
     * @return
     */
    public <T> T point(Class<T> clazz, int someIntIdJPAViaAnnotationsDescribed) {
        EntityManager em = em(Thread.currentThread(), clazz);
        T mayBeHollow = em.getReference(clazz, someIntIdJPAViaAnnotationsDescribed);
        return mayBeHollow;
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @param jpaclause
     * @param params
     * @param orderByClause
     * @return
     */
    public <T> List<T> findWhere(Class<T> clazz, String jpaclause, HashMap<String, String> params, String orderByClause) {
        if (orderByClause == null) {
            orderByClause = "";
        }
        TypedQuery<T> query = em(Thread.currentThread(), clazz).createQuery(
                "SELECT c FROM " + clazz.getSimpleName() + " c WHERE " + jpaclause + orderByClause,
                clazz);
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                query.setParameter(key, value);
            }
        }
        return query.getResultList();
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @param jpaclause
     * @param params
     * @param orderByClause
     * @return
     */
    public <T> T findWhereSingle(Class<T> clazz, String jpaclause, HashMap<String, String> params, String orderByClause) {
        TypedQuery<T> query = em(Thread.currentThread(), clazz).createQuery(
                "SELECT c FROM " + clazz.getSimpleName() + " c WHERE " + jpaclause + orderByClause,
                clazz);
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                query.setParameter(key, value);
            }
        }
        return query.getSingleResult();
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @return
     */
    public <T> List<T> getAll(Class<T> clazz) {
        return getAll(clazz, null);
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @param jpaclause
     * @param params
     * @return
     */
    public <T> List<T> findWhere(Class<T> clazz, String jpaclause, HashMap<String, String> params) {
        return findWhere(clazz, jpaclause, params, null);
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @param jpaclause
     * @param params
     * @return
     */
    public <T> T findWhereSingle(Class<T> clazz, String jpaclause, HashMap<String, String> params) {
        return findWhereSingle(clazz, jpaclause, params, null);
    }

    // ========================================================================
    // PUBLIC API Retrieval (SCRIPTED and backward compatible with SpaceX and HibernationProject)
    // =====================
    public <T> List<T> findAll_MULTILIKE_restricted(Class<T> clazz, String[] names, String[] values, int comp) {
        return findAll_MULTILIKE_restricted(false, null, clazz, names, values, comp);
    }

    public <T> List<T> findAll_MATCH(Class<T> clazz, String name, String value, int comp) {
        return findAll_MATCH(false, null, clazz, name, value, comp);
    }

    public <T> List<T> findAll_MATCH_INTEGERANDSTRING(Class<T> clazz, String name, String value, String intName, int intValue) {
        return findAll_MATCH_INTEGERANDSTRING(false, null, clazz, name, value, intName, intValue);
    }

    public <T> List<T> findAll_LIKE(Class<T> clazz, String name, String likeValue, int comp) {
        return findAll_LIKE(false, null, clazz, name, likeValue, comp);
    }

    public <T> List<T> findAll_TIME_MATCH(Class<T> clazz, String name, String value, int comp, String timeName, long from, long to) {
        return findAll_TIME_MATCH(false, null, clazz, name, value, comp, timeName, from, to);
    }

    public <T> List<T> findAll_MULTILIKE_restricted(boolean sortOrder, String sortField, Class<T> clazz, String[] names, String[] values, int comp) {
        String no = "LIKE";
        if (comp != 0) {
            no = "NOT LIKE";
        }
        if (sortField != null) {
            String so = "asc";
            if (sortOrder) {
                so = "desc";
            }
            StringBuilder jpa = new StringBuilder();
            jpa.append("c.").append(names[0]).append(" ").append(no).append(" '%").append(values[0]).append("%'");
            for (int i = 1; i < values.length; i++) {
                jpa.append(" AND c.").append(names[i]).append(" ").append(no).append(" '%").append(values[i]).append("%'");
            }
            return findWhere(clazz, jpa.toString(), null, "order by c." + sortField + " " + so);
        } else {
            StringBuilder jpa = new StringBuilder();
            jpa.append("c.").append(names[0]).append(" ").append(no).append(" '%").append(values[0]).append("%'");
            for (int i = 1; i < values.length; i++) {
                jpa.append(" AND c.").append(names[i]).append(" ").append(no).append(" '%").append(values[i]).append("%'");
            }
            return findWhere(clazz, jpa.toString(), null);
        }
    }

    public <T> List<T> findAll_MATCH(boolean sortOrder, String sortField, Class<T> clazz, String name, String value, int comp) {
        if (sortField != null) {
            String so = "asc";
            if (sortOrder) {
                so = "desc";
            }
            if (comp == 0) {
                return findWhere(clazz, "c." + name + " = '" + value + "'", null, "order by c." + sortField + " " + so);
            } else {
                return findWhere(clazz, "c." + name + " != '" + value + "'", null, "order by c." + sortField + " " + so);
            }
        } else {
            if (comp == 0) {
                return findWhere(clazz, "c." + name + " = '" + value + "'", null);
            } else {
                return findWhere(clazz, "c." + name + " != '" + value + "'", null);
            }
        }
    }

    public <T> List<T> findAll_MATCH_INTEGERANDSTRING(boolean sortOrder, String sortField, Class<T> clazz, String name, String value, String intName, int intValue) {
        if (sortField != null) {
            String so = "asc";
            if (sortOrder) {
                so = "desc";
            }
            return findWhere(clazz, "c." + name + " = '" + value + "' AND c." + intName + " = " + intValue, null, "order by c." + sortField + " " + so);
        } else {
            return findWhere(clazz, "c." + name + " = '" + value + "' AND c." + intName + " = " + intValue, null);
        }
    }

    public <T> List<T> findAll_LIKE(boolean sortOrder, String sortField, Class<T> clazz, String name, String likeValue, int comp) {
        if (sortField != null) {
            String so = "asc";
            if (sortOrder) {
                so = "desc";
            }
            if (comp == 0) {
                return findWhere(clazz, "c." + name + " LIKE '%" + likeValue + "%'", null, "order by c." + sortField + " " + so);
            } else {
                return findWhere(clazz, "c." + name + " NOT LIKE '%" + likeValue + "%'", null, "order by c." + sortField + " " + so);
            }
        } else {
            if (comp == 0) {
                return findWhere(clazz, "c." + name + " LIKE '%" + likeValue + "%'", null);
            } else {
                return findWhere(clazz, "c." + name + " NOT LIKE '%" + likeValue + "%'", null);
            }
        }
    }

    public <T> List<T> findAll_TIME_MATCH(boolean sortOrder, String sortField, Class<T> clazz, String name, String value, int comp, String timeName, long from, long to) {
        if (sortField != null) {
            String so = "asc";
            if (sortOrder) {
                so = "desc";
            }
            return findWhere(clazz, "c." + timeName + " >= " + from + "L AND c." + timeName + " < " + to + "L AND c." + name + " = '" + value + "'", null, "order by c." + sortField + " " + so);
        } else {
            return findWhere(clazz, "c." + timeName + " >= " + from + "L AND c." + timeName + " < " + to + "L AND c." + name + " = '" + value + "'", null);
        }
    }

    // =================================================================================================
    // =================================================================================================
    // special retrievers and tool methods for hyperspace
    
    private ArrayList<IHyperspaceEntity> retrieveToBeUpdatedEntities(String peerId, ArrayList<UpdateEntry> eb) {
        ArrayList<IHyperspaceEntity> res = new ArrayList<>();
        eb.addAll(findAll_MATCH(UpdateEntry.class, "peer", peerId, 0));
        HashMap<String, List<String>> buffer = new HashMap<>();
        for (UpdateEntry updateEntry : eb) {
            String clazzname = updateEntry.getClazzname();
            String target = updateEntry.getTarget();
            if (!buffer.containsKey(clazzname)) {
                buffer.put(clazzname, new ArrayList<>());
            }
            buffer.get(clazzname).add(target);
        }
        for (Map.Entry<String, List<String>> entry : buffer.entrySet()) {

            try {
                final Class<?> aClass = ReflectUtils.getClass(entry.getKey());
                final List<String> ids = entry.getValue();
                for (String id : ids) {
                    res.add((IHyperspaceEntity)get(aClass, id));
                }
            } catch (ClassNotFoundException ex) {

            }
        }
        return res;
    }

    private List<DeleteMessage> issueToBeDeletedMessages(String peerId, ArrayList<DeleteEntry> eb) {
        eb.addAll(findAll_MATCH(DeleteEntry.class, "peer", peerId, 0));
        final long t = System.currentTimeMillis();
        ArrayList<DeleteMessage> res = new ArrayList<>();
        for (DeleteEntry de : eb) {
            String clazzname = de.getClazzname();
            String target = de.getTarget();
            DeleteMessage m = new DeleteMessage();
            m.setIssued(t);
            m.setPeerOrigin(myId);
            m.setTargetClazz(clazzname);
            m.setTargetUid(target);
            res.add(m);
        }
        return res;
    }

    private EJSONObject ser(IHyperspaceEntity o) {
        EJSONObject res = new EJSONObject();
        res.putString("clazz", o.getClass().getName());
        res.putString("uid", o.uid());
        // ...
        return null;
    }

    private <T> T deser(EJSONObject o, Class<T> clazz) {
        return null;
    }
}
