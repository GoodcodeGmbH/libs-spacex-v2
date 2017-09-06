/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2;

import ch.goodcode.libs.io.EnhancedFilesystemIO;
import ch.goodcode.libs.utils.GOOUtils;
import ch.goodcode.libs.utils.dataspecs.EJSONArray;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import ch.goodcode.spacex.v2.engine.MiniClient;
import ch.goodcode.spacex.v2.engine.MiniServer;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;

/**
 *
 * @author Paolo Domenighetti
 */
public class SpaceV2 {

    // ======================================================================================================================================
    private static final int PURGE_LIMIT = 50;
    private static final long PURGE_TIMEOUT = 20 * GOOUtils.TIME_MINUTES;
    private static final int MAGIC_BATCH_BOUNDARY = 50;
    private static final int MAGIC_BATCH_DIVISOR = 20_000;
    private static final int MAGIC_ASYNCH_BOUNDARY = 10_000;
    private static int LL;
    private final boolean IS_REAL_SPACE;
    private String myId;

    public String geSpaceId() {
        return myId;
    }

    private LogBuffer LOG;
    private EntityManagerFactory emf;
    private final HashMap<Long, EntityManager> ems = new HashMap<>();
    private final HashMap<Long, Long> emsT = new HashMap<>();
    private long emsC = 0L;
    private final EJSONObject odbConf;
    private final String optHostFull;
    private String optUser, optPass;
    // -
    private final EJSONObject spaceConf;
    private MiniServer server;
    private SpacePeer[] peers;
    private final HashMap<String, MiniClient> clients = new HashMap<>();

    /**
     *
     * @param logLevel
     * @param odbConfFilePath
     * @param spaceConfFilePath
     */
    public SpaceV2(int logLevel, String odbConfFilePath, String spaceConfFilePath) {
        LL = logLevel;
        IS_REAL_SPACE = spaceConfFilePath != null;
        odbConf = new EJSONObject(EnhancedFilesystemIO.fileRead(new File(odbConfFilePath)).toString());
        if (IS_REAL_SPACE) {
            spaceConf = new EJSONObject(EnhancedFilesystemIO.fileRead(new File(spaceConfFilePath)).toString());
        } else {
            spaceConf = null;
        }
        optHostFull = null;
    }

    /**
     * For DEBUG Constructor
     *
     * @param logLevel
     * @param hostStringFull
     * @param user
     * @param pass
     */
    public SpaceV2(int logLevel, String hostStringFull, String user, String pass) {
        LL = logLevel;
        IS_REAL_SPACE = false;
        odbConf = null;
        spaceConf = null;
        optHostFull = hostStringFull;
        optUser = user;
        optPass = pass;

    }

    /**
     * For DEBUG Constructor
     *
     * @param logLevel
     * @param odbFilePath
     */
    public SpaceV2(int logLevel, String odbFilePath) {
        LL = logLevel;
        IS_REAL_SPACE = false;
        odbConf = null;
        spaceConf = null;
        optHostFull = odbFilePath;
    }

    /**
     * For DEBUG Constructor
     *
     * @param logLevel
     * @param debugId
     */
    public SpaceV2(int logLevel, long debugId) {
        this(logLevel, "$objectdb/db/debug_" + debugId + ".odb");
    }

    /**
     *
     * @throws Exception
     */
    public void start() throws Exception {
        //LOG.o("Starting SV2 instance...");
        if (optUser == null) {
            emf = Persistence.createEntityManagerFactory(
                    optHostFull);
        } else if (odbConf == null) {
            Map<String, String> properties = new HashMap<>();
            properties.put("javax.persistence.jdbc.user", optUser);
            properties.put("javax.persistence.jdbc.password", optPass);
            emf = Persistence.createEntityManagerFactory(
                    optHostFull,
                    properties);
        } else {
            Map<String, String> properties = new HashMap<>();
            properties.put("javax.persistence.jdbc.user", odbConf.getString("odbUser"));
            properties.put("javax.persistence.jdbc.password", odbConf.getString("odbPass"));
            emf = Persistence.createEntityManagerFactory(
                    "objectdb://" + odbConf.getString("odbHost") + ":" + odbConf.getInteger("odbPort") + "/" + odbConf.getString("memUid") + ".odb",
                    properties);
        }
        // put in place MEM TIER
        // put in place COM/P2P TIER
        if (IS_REAL_SPACE) {
            //LOG.o("SV2 Space tier detected, starting it...");
            server = new MiniServer(this, spaceConf.getInteger("listeningPort"));
            server.startInThread();

            for (SpacePeer peer : peers) {
                MiniClient c = new MiniClient(peer.getHost(), peer.getPort());
                c.start();
            }

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

    public void listen_parseAndWorkoutListenedObject(String peerId, EJSONObject payload) {

    }

    public void listen_announce(String peerId, EJSONObject access) {

    }

    public void listen_meta(String peerId, EJSONObject meta) {

    }

    public void listen_error(String peerId, EJSONObject error) {

    }

    private void sendObjectToPeer(String peerId, EJSONObject payload) {

    }

    private void sendObjectsToPeer(String peerId, EJSONArray payload) {

    }

    public String encrypt() {
        return "";
    }

    public String decrypt() {
        return "";
    }

    /**
     *
     * @throws Exception
     */
    public void stop() throws Exception {
        for (Map.Entry<Long, EntityManager> entry : ems.entrySet()) {
            EntityManager em = entry.getValue();
            em.close();
        }
        ems.clear();
        emf.close();
    }

    private EntityManager em(Thread currentThread) {
        if (emsC > PURGE_LIMIT) {
            emsC = 0L;
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    ArrayList<Long> tbDelTids = new ArrayList<>();
                    for (Map.Entry<Long, Long> entry : emsT.entrySet()) {
                        Long tid = entry.getKey();
                        Long lasrtSeen = entry.getValue();
                        if (now - lasrtSeen > PURGE_TIMEOUT) {
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
            ems.put(currentThread.getId(), emf.createEntityManager());
            emsC++;
        }
        emsT.put(currentThread.getId(), System.currentTimeMillis());
        return ems.get(currentThread.getId());
    }

    // ========================================================================
    // Space utility methods
    // (they very well may use the public api of course for meta types entities)
    private <T> void screate(T item) {

    }

    private <T> void supdate(T item) {

    }

    private <T> void sdelete(T item) {

    }

    private <T> void screate(int size, List<T> items) {
        if (size < MAGIC_ASYNCH_BOUNDARY) {

        } else {

        }
    }

    private <T> void supdate(int size, List<T> items) {
        if (size < MAGIC_ASYNCH_BOUNDARY) {

        } else {

        }
    }

    private <T> void sdelete(int size, List<T> items) {
        if (size < MAGIC_ASYNCH_BOUNDARY) {

        } else {

        }
    }

    // ========================================================================
    // PUBLIC API CRUD
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
        EntityManager em = em(Thread.currentThread());
        try {
            em.getTransaction().begin();
            em.persist(item);
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            } else {
                if (IS_REAL_SPACE) {
                    screate(item);
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
        EntityManager em = em(Thread.currentThread());
        try {
            em.getTransaction().begin();
            em.merge(item);
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            } else {
                if (IS_REAL_SPACE) {
                    supdate(item);
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
        EntityManager em = em(Thread.currentThread());
        try {
            em.getTransaction().begin();
            em.remove(item);
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            } else {
                if (IS_REAL_SPACE) {
                    sdelete(item);
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
            EntityManager em = em(Thread.currentThread());
            try {
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
            } finally {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                } else {
                    if (IS_REAL_SPACE) {
                        screate(size, items);
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
            EntityManager em = em(Thread.currentThread());
            try {
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
            } finally {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                } else {
                    if (IS_REAL_SPACE) {
                        supdate(size, items);
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
            EntityManager em = em(Thread.currentThread());
            try {
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
            } finally {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                } else {
                    if (IS_REAL_SPACE) {
                        sdelete(size, items);
                    }
                    deleteForCacheListeners(items);
                }
            }
        }
    }

    // ========================================================================
    // PUBLIC API Retrieval (BASE)
    // =====================
    // ..
    /**
     *
     * @param <T>
     * @param clazz
     * @param orderByClause
     * @return
     */
    public <T> List<T> getAll(Class<T> clazz, String orderByClause) {
        TypedQuery<T> query = em(Thread.currentThread()).createQuery(
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
        EntityManager em = em(Thread.currentThread());
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
        EntityManager em = em(Thread.currentThread());
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
        EntityManager em = em(Thread.currentThread());
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
        EntityManager em = em(Thread.currentThread());
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
        EntityManager em = em(Thread.currentThread());
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
        EntityManager em = em(Thread.currentThread());
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
        if(orderByClause == null) {
            orderByClause = "";
        }
        TypedQuery<T> query = em(Thread.currentThread()).createQuery(
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
        TypedQuery<T> query = em(Thread.currentThread()).createQuery(
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
            return findWhere(clazz, "c." + timeName + " >= " + from + " AND c." + timeName + " < " + to + " AND c." + name + " = '" + value + "'", null, "order by c." + sortField + " " + so);
        } else {
            return findWhere(clazz, "c." + timeName + " >= " + from + " AND c." + timeName + " < " + to + " AND c." + name + " = '" + value + "'", null);
        }
    }
}
