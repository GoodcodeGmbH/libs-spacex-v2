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
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import ch.goodcode.spacex.v2.compute.ODBEmbeddedUnit;
import ch.goodcode.spacex.v2.compute.ObjectDBUnit;
import ch.goodcode.spacex.v2.compute.RegVar;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

/**
 *
 * @author Paolo Domenighetti
 */
public final class SpaceV2 {

    // ======================================================================================================================================
    public static final int EMF_SIZE_LIMIT = 1_000_000;
    public static final int EM_PURGE_LIMIT = 50;
    public static final long EM_PURGE_TIMEOUT = 20 * GOOUtils.TIME_MINUTES;
    private static final int MAGIC_BATCH_DIVISOR = 20_000;

    //-
    private final String MY_ID;

    // -
    private final ThreadManager TM = new ThreadManager(1, 100);
    private final ThreadLocal<EntityManager> EM_CONTEXT = new ThreadLocal<>();

    //-
    private LogBuffer LOG;
    private final String optHostFull;
    private String optUser, optPass;
    private ObjectDBUnit ODBUNIT;

    // -
    private final EJSONObject odbConf;
    private final EJSONObject spaceConf;

    /**
     *
     * @param uid
     * @param logPath
     * @param logLevel
     * @param odbConfFilePath
     * @param spaceConfFilePath
     */
    public SpaceV2(String uid, String logPath, int logLevel, String odbConfFilePath, String spaceConfFilePath) {
        LOG = new LogBuffer("spacev2-" + uid, logPath, 1, logLevel);
        odbConf = new EJSONObject(EnhancedFilesystemIO.fileRead(new File(odbConfFilePath)).toString());
        spaceConf = new EJSONObject(EnhancedFilesystemIO.fileRead(new File(spaceConfFilePath)).toString());
        optHostFull = null;
        this.MY_ID = uid;
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
        odbConf = null;
        spaceConf = null;
        optHostFull = hostStringFull;
        optUser = user;
        optPass = pass;
        this.MY_ID = uid;
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
        odbConf = null;
        spaceConf = null;
        optHostFull = odbFilePath;
        this.MY_ID = uid;
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

    private boolean isDebug() {
        return optUser == null || odbConf == null;
    }

    public String geSpaceId() {
        return MY_ID;
    }

    /**
     *
     * @throws Exception
     */
    public void start() throws Exception {
        LOG.o("Starting SV2 instance '" + MY_ID + "'...");

        // put in place MEM TIER ---------------------------------------------------
        if (optUser == null) {

            // DEBUG setup (uses default config in odb root folder)
            // no units are space
            // here odb limits apply (10 clazzes, 10^6 entities per clazz)
            ODBUNIT = new ODBEmbeddedUnit(optHostFull);
            ODBUNIT.initialize();
            LOG.o("Mod0 (debug) detected: main ef only with path " + optHostFull);

        } else if (odbConf == null) {

            // DEBUG setup (uses default config in odb root folder)
            // no units are space
            // here odb limits apply (10 clazzes, 10^6 entities per clazz)
            ODBUNIT = new ODBEmbeddedUnit(optHostFull, optUser, optPass);
            ODBUNIT.initialize();
            LOG.o("Mod1 ('configless', debug) detected: main ef only with path " + optHostFull + " and user access");

        } else {

            LOG.o("................... 100%: odb Config file(s) detected and properly parsed.");
            System.setProperty("objectdb.conf", "/my/objectdb.conf"); // TODO
            // TODO main emf must exist always, if not used it is a service local odb embedded runtime

        }

    }

    private final HashMap<String, ILevel3CacheListener<?>> LEVEL3CACHELISTENERS = new HashMap<>();

    /**
     *
     * @param <T>
     * @param clazz
     * @param aLevel3CacheListener
     */
    public <T extends IV2Entity> void registerL3CacheListener(Class<T> clazz, ILevel3CacheListener<T> aLevel3CacheListener) {
        LEVEL3CACHELISTENERS.put(clazz.getSimpleName(), aLevel3CacheListener);
    }

    private synchronized <T extends IV2Entity> void createForCacheListeners(T object) {
        final String name = object.getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3CacheListener<T> y = (ILevel3CacheListener<T>) LEVEL3CACHELISTENERS.get(name);
            if (y.deferredProcessing()) {
                TM.fetchCached(() -> {
                    y.create(object);
                });
            } else {
                y.create(object);
            }
        }
    }

    private synchronized <T extends IV2Entity> void updateForCacheListeners(T object) {
        final String name = object.getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3CacheListener<T> y = (ILevel3CacheListener<T>) LEVEL3CACHELISTENERS.get(name);
            if (y.deferredProcessing()) {
                TM.fetchCached(() -> {
                    y.update(object);
                });
            } else {
                y.update(object);
            }
        }
    }

    private synchronized <T extends IV2Entity> void deleteForCacheListeners(T object) {
        final String name = object.getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3CacheListener<T> y = (ILevel3CacheListener<T>) LEVEL3CACHELISTENERS.get(name);
            if (y.deferredProcessing()) {
                TM.fetchCached(() -> {
                    y.delete(object);
                });
            } else {
                y.delete(object);
            }
        }
    }

    private synchronized <T extends IV2Entity> void createForCacheListeners(List<T> objects) {
        final String name = objects.get(0).getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3CacheListener<T> y = ((ILevel3CacheListener<T>) LEVEL3CACHELISTENERS.get(name));
            if (y.deferredProcessing()) {
                TM.fetchCached(() -> {
                    y.create(objects);
                });
            } else {
                y.create(objects);
            }
        }
    }

    private synchronized <T extends IV2Entity> void updateForCacheListeners(List<T> objects) {
        final String name = objects.get(0).getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3CacheListener<T> y = ((ILevel3CacheListener<T>) LEVEL3CACHELISTENERS.get(name));
            if (y.deferredProcessing()) {
                TM.fetchCached(() -> {
                    y.update(objects);
                });
            } else {
                y.update(objects);
            }
        }
    }

    private synchronized <T extends IV2Entity> void deleteForCacheListeners(List<T> objects) {
        final String name = objects.get(0).getClass().getSimpleName();
        if (LEVEL3CACHELISTENERS.containsKey(name)) {
            ILevel3CacheListener<T> y = ((ILevel3CacheListener<T>) LEVEL3CACHELISTENERS.get(name));
            if (y.deferredProcessing()) {
                TM.fetchCached(() -> {
                    y.delete(objects);
                });
            } else {
                y.delete(objects);
            }
        }
    }

    /**
     *
     * @throws Exception
     */
    public void stop() throws Exception {
        LOG.o("Now stopping SV2 '" + MY_ID + "'...");
        ODBUNIT.dispose();
        LOG.o("Stopped SV2 '" + MY_ID + "', goodbye.");
        LOG.s();
    }

    private <T extends IV2Entity> void emBegin(Class<T> clazz) {
        EntityManager found = ODBUNIT.em(clazz);
        EM_CONTEXT.set(found);
    }

    private void emEnd() {
        EM_CONTEXT.remove();
    }

    // ========================================================================
    // ========================================================================
    // ========================================================================
    // PUBLIC API C(R)UD
    // =====================
    /**
     *
     * ----------------------------------------------------------------
     *
     * General behavior for persistency are the following: 1) the proper
     * thread-related entity manager is fetched 2) JPA crud transaction executed
     * 2a) if NOT ok, then rollback transaction, warn and keep going without any
     * other JPA invokations; 2b) else apply JPA crud action and finally: -
     * check for hyperspace mod, if yes apply screate(...) - ( may be asynch)
     * see ref. - apply createForCacheListeners(...) -(may be asynch) see ref.
     *
     * @param <T>
     * @param item
     */
    public <T extends IV2Entity> void create(T item) {
        emBegin(item.getClass());
        try {
            if (EM_CONTEXT.get() != null) {
                EM_CONTEXT.get().getTransaction().begin();
                EM_CONTEXT.get().persist(item);
                EM_CONTEXT.get().getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in create()");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in create()", e);
        } finally {
            if (EM_CONTEXT.get() != null && EM_CONTEXT.get().getTransaction().isActive()) {
                EM_CONTEXT.get().getTransaction().rollback();
                LOG.i("Faulty transaction in create() has been rolled back.");
            } else if (EM_CONTEXT.get() != null) {
                createForCacheListeners(item);
            }
        }
        emEnd();
    }

    /**
     *
     * @param <T>
     * @param item
     */
    public <T extends IV2Entity> void update(T item) {
        emBegin(item.getClass());
        try {
            if (EM_CONTEXT.get() != null) {
                EM_CONTEXT.get().getTransaction().begin();
                EM_CONTEXT.get().merge(item);
                EM_CONTEXT.get().getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in update()");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in update()", e);
        } finally {
            if (EM_CONTEXT.get() != null && EM_CONTEXT.get().getTransaction().isActive()) {
                EM_CONTEXT.get().getTransaction().rollback();
                LOG.i("Faulty transaction in delete() has been rolled back.");
            } else if (EM_CONTEXT.get() != null) {
                updateForCacheListeners(item);
            }
        }
        emEnd();
    }

    /**
     *
     * @param <T>
     * @param item
     */
    public <T extends IV2Entity> void delete(T item) {
        emBegin(item.getClass());
        try {
            if (EM_CONTEXT.get() != null) {
                EM_CONTEXT.get().getTransaction().begin();
                EM_CONTEXT.get().remove(item);
                EM_CONTEXT.get().getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in delete()");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in delete()", e);
        } finally {
            if (EM_CONTEXT.get() != null && EM_CONTEXT.get().getTransaction().isActive()) {
                EM_CONTEXT.get().getTransaction().rollback();
                LOG.i("Faulty transaction in delete() has been rolled back.");
            } else if (EM_CONTEXT.get() != null) {
                deleteForCacheListeners(item);
            }
        }
        emEnd();
    }

    /**
     *
     * @param <T>
     * @param items
     */
    public <T extends IV2Entity> void create(List<T> items) {

        emBegin(items.get(0).getClass());
        try {
            if (EM_CONTEXT.get() != null) {
                EM_CONTEXT.get().getTransaction().begin();
                int i = 0;
                for (T it : items) {
                    EM_CONTEXT.get().persist(it);
                    if ((i % MAGIC_BATCH_DIVISOR) == 0) {
                        EM_CONTEXT.get().getTransaction().commit();
                        EM_CONTEXT.get().clear();
                        EM_CONTEXT.get().getTransaction().begin();
                    }
                    i++;
                }
                EM_CONTEXT.get().getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in create(List<>)");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in create(List<>)", e);
        } finally {
            if (EM_CONTEXT.get() != null && EM_CONTEXT.get().getTransaction().isActive()) {
                EM_CONTEXT.get().getTransaction().rollback();
                LOG.i("Faulty transaction in create(List<>) has been rolled back.");
            } else if (EM_CONTEXT.get() != null) {
                createForCacheListeners(items);
            }
        }

        emEnd();
    }

    /**
     *
     * @param <T>
     * @param items
     */
    public <T extends IV2Entity> void update(List<T> items) {

        emBegin(items.get(0).getClass());
        try {
            if (EM_CONTEXT.get() != null) {
                EM_CONTEXT.get().getTransaction().begin();
                int i = 0;
                for (T it : items) {
                    EM_CONTEXT.get().merge(it);
                    if ((i % MAGIC_BATCH_DIVISOR) == 0) {
                        EM_CONTEXT.get().getTransaction().commit();
                        EM_CONTEXT.get().clear();
                        EM_CONTEXT.get().getTransaction().begin();
                    }
                    i++;
                }
                EM_CONTEXT.get().getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in update(List<>)");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in update(List<>)", e);
        } finally {
            if (EM_CONTEXT.get() != null && EM_CONTEXT.get().getTransaction().isActive()) {
                EM_CONTEXT.get().getTransaction().rollback();
                LOG.i("Faulty transaction in update(List<>) has been rolled back.");
            } else if (EM_CONTEXT.get() != null) {
                updateForCacheListeners(items);
            }
        }

        emEnd();
    }

    /**
     *
     * @param <T>
     * @param items
     */
    public <T extends IV2Entity> void delete(List<T> items) {

        emBegin(items.get(0).getClass());
        try {
            if (EM_CONTEXT.get() != null) {
                EM_CONTEXT.get().getTransaction().begin();
                int i = 0;
                for (T it : items) {
                    EM_CONTEXT.get().remove(it);
                    if ((i % MAGIC_BATCH_DIVISOR) == 0) {
                        EM_CONTEXT.get().getTransaction().commit();
                        EM_CONTEXT.get().clear();
                        EM_CONTEXT.get().getTransaction().begin();
                    }
                    i++;
                }
                EM_CONTEXT.get().getTransaction().commit();
            } else {
                LOG.e("Null Entity manager in delete(List<>)");
            }
        } catch (Exception e) {
            LOG.e("Transaction exception in delete(List<>)", e);
        } finally {
            if (EM_CONTEXT.get() != null && EM_CONTEXT.get().getTransaction().isActive()) {
                EM_CONTEXT.get().getTransaction().rollback();
                LOG.i("Faulty transaction in delete(List<>) has been rolled back.");
            } else if (EM_CONTEXT.get() != null) {
                deleteForCacheListeners(items);
            }
        }

        emEnd();
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
    public <T extends IV2Entity> List<T> getAll(Class<T> clazz, String orderByClause) {
        emBegin(clazz);
        TypedQuery<T> query = null;
        synchronized (query) {
            query = EM_CONTEXT.get().createQuery(
                    "SELECT c FROM Object c WHERE c instanceof " + clazz.getSimpleName(),
                    clazz);
            emEnd();
            return query.getResultList();
        }
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
    public <T extends IV2Entity> T get(Class<T> clazz, String someStringUidJPAViaAnnotationsDescribed) {
        emBegin(clazz);
        T fullObject = null;
        synchronized (fullObject) {
            fullObject = EM_CONTEXT.get().find(clazz, someStringUidJPAViaAnnotationsDescribed);
            emEnd();
            return fullObject;
        }
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
    public <T extends IV2Entity> T point(Class<T> clazz, String someStringUidJPAViaAnnotationsDescribed) {
        emBegin(clazz);
        T mayBeHollow = null;
        synchronized (mayBeHollow) {
            mayBeHollow = EM_CONTEXT.get().getReference(clazz, someStringUidJPAViaAnnotationsDescribed);
            emEnd();
            return mayBeHollow;
        }
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @param someLongIdJPAViaAnnotationsDescribed
     * @return
     */
    public <T extends IV2Entity> T get(Class<T> clazz, long someLongIdJPAViaAnnotationsDescribed) {
        emBegin(clazz);
        T fullObject = null;
        synchronized (fullObject) {
            fullObject = EM_CONTEXT.get().find(clazz, someLongIdJPAViaAnnotationsDescribed);
            emEnd();
            return fullObject;
        }
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
    public <T extends IV2Entity> T point(Class<T> clazz, long someLongIdJPAViaAnnotationsDescribed) {
        emBegin(clazz);
        T mayBeHollow = null;
        synchronized (mayBeHollow) {
            mayBeHollow = EM_CONTEXT.get().getReference(clazz, someLongIdJPAViaAnnotationsDescribed);
            emEnd();
            return mayBeHollow;
        }
    }

    /**
     * Note: this is also backward compatible with both SpaceX and HibProject
     *
     * @param <T>
     * @param clazz
     * @param someIntIdJPAViaAnnotationsDescribed
     * @return
     */
    public <T extends IV2Entity> T get(Class<T> clazz, int someIntIdJPAViaAnnotationsDescribed) {
        emBegin(clazz);
        T fullObject = null;
        synchronized (fullObject) {
            fullObject = EM_CONTEXT.get().find(clazz, someIntIdJPAViaAnnotationsDescribed);
            emEnd();
            return fullObject;
        }
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
    public <T extends IV2Entity> T point(Class<T> clazz, int someIntIdJPAViaAnnotationsDescribed) {
        emBegin(clazz);
        T mayBeHollow = null;
        synchronized (mayBeHollow) {
            mayBeHollow = EM_CONTEXT.get().getReference(clazz, someIntIdJPAViaAnnotationsDescribed);
            emEnd();
            return mayBeHollow;
        }
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
    public <T extends IV2Entity> List<T> findWhere(Class<T> clazz, String jpaclause, HashMap<String, String> params, String orderByClause) {
        emBegin(clazz);
        if (orderByClause == null) {
            orderByClause = "";
        }
        TypedQuery<T> query = null;
        synchronized (query) {
            query = EM_CONTEXT.get().createQuery(
                    "SELECT c FROM " + clazz.getSimpleName() + " c WHERE " + jpaclause + orderByClause,
                    clazz);
            if (params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    query.setParameter(key, value);
                }
            }
            emEnd();
            return query.getResultList();
        }
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
    public <T extends IV2Entity> T findWhereSingle(Class<T> clazz, String jpaclause, HashMap<String, String> params, String orderByClause) {
        emBegin(clazz);
        TypedQuery<T> query = null;
        synchronized (query) {
            query = EM_CONTEXT.get().createQuery(
                    "SELECT c FROM " + clazz.getSimpleName() + " c WHERE " + jpaclause + orderByClause,
                    clazz);
            if (params != null) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    query.setParameter(key, value);
                }
            }
            emEnd();
            return query.getSingleResult();
        }
    }

    /**
     *
     * @param <T>
     * @param clazz
     * @return
     */
    public <T extends IV2Entity> List<T> getAll(Class<T> clazz) {
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
    public <T extends IV2Entity> List<T> findWhere(Class<T> clazz, String jpaclause, HashMap<String, String> params) {
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
    public <T extends IV2Entity> T findWhereSingle(Class<T> clazz, String jpaclause, HashMap<String, String> params) {
        return findWhereSingle(clazz, jpaclause, params, null);
    }

    // ========================================================================
    // PUBLIC API Retrieval (SCRIPTED and backward compatible with SpaceX and HibernationProject)
    // =====================
    public <T extends IV2Entity> List<T> findAll_MULTILIKE_restricted(Class<T> clazz, String[] names, String[] values, int comp) {
        return findAll_MULTILIKE_restricted(false, null, clazz, names, values, comp);
    }

    public <T extends IV2Entity> List<T> findAll_MATCH(Class<T> clazz, String name, String value, int comp) {
        return findAll_MATCH(false, null, clazz, name, value, comp);
    }

    public <T extends IV2Entity> List<T> findAll_MATCH_INTEGERANDSTRING(Class<T> clazz, String name, String value, String intName, int intValue) {
        return findAll_MATCH_INTEGERANDSTRING(false, null, clazz, name, value, intName, intValue);
    }

    public <T extends IV2Entity> List<T> findAll_LIKE(Class<T> clazz, String name, String likeValue, int comp) {
        return findAll_LIKE(false, null, clazz, name, likeValue, comp);
    }

    public <T extends IV2Entity> List<T> findAll_TIME_MATCH(Class<T> clazz, String name, String value, int comp, String timeName, long from, long to) {
        return findAll_TIME_MATCH(false, null, clazz, name, value, comp, timeName, from, to);
    }

    public <T extends IV2Entity> List<T> findAll_MULTILIKE_restricted(boolean sortOrder, String sortField, Class<T> clazz, String[] names, String[] values, int comp) {
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

    public <T extends IV2Entity> List<T> findAll_MATCH(boolean sortOrder, String sortField, Class<T> clazz, String name, String value, int comp) {
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

    public <T extends IV2Entity> List<T> findAll_MATCH_INTEGERANDSTRING(boolean sortOrder, String sortField, Class<T> clazz, String name, String value, String intName, int intValue) {
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

    public <T extends IV2Entity> List<T> findAll_LIKE(boolean sortOrder, String sortField, Class<T> clazz, String name, String likeValue, int comp) {
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

    public <T extends IV2Entity> List<T> findAll_TIME_MATCH(boolean sortOrder, String sortField, Class<T> clazz, String name, String value, int comp, String timeName, long from, long to) {
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
            return findWhere(clazz, "c." + timeName + " >= " + from + "L AND c." + timeName + " < " + to, null, "order by c." + sortField + " " + so);
        } else {
            return findWhere(clazz, "c." + timeName + " >= " + from + "L AND c." + timeName + " < " + to, null);
        }
    }
}
