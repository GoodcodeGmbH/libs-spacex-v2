/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2;

import ch.goodcode.libs.logging.LogBuffer;
import ch.goodcode.libs.io.EnhancedFilesystemIO;
import ch.goodcode.libs.threading.ThreadManager;
import ch.goodcode.libs.utils.GOOUtils;
import ch.goodcode.libs.utils.dataspecs.EJSONArray;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import ch.goodcode.spacex.v2.compute.RegVar;
//import ch.goodcode.spacex.v2.compute.TokensPolicy;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int EMF_SIZE_LIMIT = 1_000_000;
    public static final int EM_PURGE_LIMIT = 50;
    public static final long EM_PURGE_TIMEOUT = 20 * GOOUtils.TIME_MINUTES;
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
    private final ConcurrentHashMap<String, EntityManagerFactory> emfsMAP = new ConcurrentHashMap<>();
//    private final ConcurrentHashMap<String, TokensPolicy> tokensPolicies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, EntityManager> ems = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> emsT = new ConcurrentHashMap<>();
    private long emsC = 0L;
    private final EJSONObject odbConf;
    private final String optHostFull;
    private String optUser, optPass;
    private final ThreadManager tmanager = new ThreadManager(100, 100);

    // -
    private final EJSONObject spaceConf;

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
        LOG = new LogBuffer("spacev2-" + uid, logPath, 1, logLevel);
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
        LOG = new LogBuffer("spacev2-" + uid, logPath, 1, logLevel);
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
            ems.put(0L, mainEMF.createEntityManager());
            System.err.println("UUAAAARGH DEBUG!!!!");

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

            LOG.o("................... 100%: odb Config file(s) detected and properly parsed.");

            if (!IS_REAL_SPACE) {

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
//                            tokensPolicies.put(dt, new TokensPolicy(tokens, unitJson.getString("tokensTypeRule"), unitJson.getString("tokensField")));

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
            } else {
                LOG.o("SV2 Space enabled, deploying and starting it on port " + spaceConf.getInteger("listeningPort") + "...");
                // prepare the main listening server for announces and objects:

                LOG.o("SV2 Space is ready.");
            }
        }

        if (IS_REAL_SPACE) {
            LOG.o("SV2 full instance '" + myId + "' with its Space has properly started. Have a nice day.");
        } else {
            LOG.o("SV2 instance '" + myId + "' has properly started. Have a nice day.");
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

    /**
     *
     * @throws Exception
     */
    public void stop() throws Exception {
        LOG.o("Now stopping SV2 '" + myId + "'...");

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

    private synchronized <T> EntityManager em(Class<T> clazz, boolean write) {
        if (!IS_REAL_SPACE) {
            return emLocalOnly(Thread.currentThread(), clazz);
        } else {
            return emRealSpace(Thread.currentThread(), clazz, write);
        }
    }

    private <T> EntityManager emLocalOnly(Thread currentThread, Class<T> clazz) {
        return ems.get(0L);
//        if (false) {
//            return null; // <<-- in token processing...
//        } else {
//            if (emsC > EM_PURGE_LIMIT) {
//                emsC = 0L;
//                (new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        long now = System.currentTimeMillis();
//                        ArrayList<Long> tbDelTids = new ArrayList<>();
//                        for (Map.Entry<Long, Long> entry : emsT.entrySet()) {
//                            Long tid = entry.getKey();
//                            Long lasrtSeen = entry.getValue();
//                            if (now - lasrtSeen > EM_PURGE_TIMEOUT) {
//                                tbDelTids.add(tid);
//                            }
//                        }
//                        for (Long threadID : tbDelTids) {
//                            ems.remove(threadID);
//                            emsT.remove(threadID);
//                        }
//                    }
//                })).start();
//            }
//            if (!ems.containsKey(currentThread.getId())) {
//                if (emfsMAP.containsKey(clazz.getSimpleName())) {
//                    ems.put(currentThread.getId(), emfsMAP.get(clazz.getSimpleName()).createEntityManager());
//                } else {
//                    ems.put(currentThread.getId(), mainEMF.createEntityManager());
//                }
//                emsC++;
//            }
//            emsT.put(currentThread.getId(), System.currentTimeMillis());
//            return ems.get(currentThread.getId());
//        }
    }

    private <T> EntityManager emRealSpace(Thread currentThread, Class<T> clazz, boolean write) {
        return null;
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
        EntityManager em = em(item.getClass(), true);
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
                LOG.i("Faulty transaction in create() has been rolled back.");
            } else if (em != null) {
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
        EntityManager em = em(item.getClass(), true);
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
                LOG.i("Faulty transaction in delete() has been rolled back.");
            } else if (em != null) {
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
        EntityManager em = em(item.getClass(), true);
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
                LOG.i("Faulty transaction in delete() has been rolled back.");
            } else if (em != null) {
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
            EntityManager em = em(items.get(0).getClass(), true);
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
                    LOG.i("Faulty transaction in create(List<>) has been rolled back.");
                } else if (em != null) {
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
            EntityManager em = em(items.get(0).getClass(), true);

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
                    LOG.i("Faulty transaction in update(List<>) has been rolled back.");
                } else if (em != null) {
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
            EntityManager em = em(items.get(0).getClass(), true);
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
                    LOG.i("Faulty transaction in delete(List<>) has been rolled back.");
                } else if (em != null) {
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
     * @param key a RegVar mapping key
     * @return the value of the registered RegVar, or null if one of those 3 is
     * met: - the key doesn't exist - the RegVar itself is null (? rare, only a
     * bug rises that) - the value for the existing key is really null
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
     * @param key a RegVar mapping key
     * @param value the value mapped by the key
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
        TypedQuery<T> query = em(clazz, false).createQuery(
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
        EntityManager em = em(clazz, false);
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
        EntityManager em = em(clazz, false);
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
        EntityManager em = em(clazz, false);
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
        EntityManager em = em(clazz, false);
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
        EntityManager em = em(clazz, false);
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
        EntityManager em = em(clazz, false);
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
        TypedQuery<T> query = em(clazz, false).createQuery(
                "SELECT c FROM " + clazz.getSimpleName() + " c WHERE " + jpaclause + " " + orderByClause,
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
        TypedQuery<T> query = em(clazz, false).createQuery(
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

    public <T extends IV2Entity> List<T> findAll_TIME(boolean sortOrder, String sortField, Class<T> clazz, String timeName, long from, long to) {
        if (sortField != null) {
            String so = "asc";
            if (sortOrder) {
                so = "desc";
            }
            return findWhere(clazz, "c." + timeName + " >= " + from + "L AND c." + timeName + " < " + to +"L", null, "order by c." + sortField + " " + so);
        } else {
            return findWhere(clazz, "c." + timeName + " >= " + from + "L AND c." + timeName + " < " + to +"L", null);
        }
    }
}
