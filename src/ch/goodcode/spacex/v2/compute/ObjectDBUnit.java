/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.spacex.v2.SpaceV2;
import ch.goodcode.spacex.v2.tests.SpaceV2Debug;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 *
 * @author Paolo Domenighetti
 */
public abstract class ObjectDBUnit {

    protected EntityManagerFactory EMF;
    protected final ConcurrentHashMap<Long, EntityManager> emsO = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Long, Long> emsT = new ConcurrentHashMap<>();
    protected long emsC = 0L;

    public abstract void initialize();
    protected abstract void preDispose();

    /**
     * 
     */
    public void dispose() {
        preDispose();
        for (Map.Entry<Long, EntityManager> entry : emsO.entrySet()) {
            EntityManager value = entry.getValue();
            value.close();
        }
        emsO.clear();
        emsT.clear();
        EMF.close();
    }

    /**
     * 
     * @param <T>
     * @param clazz
     * @return 
     */
    public <T> EntityManager em(Class<T> clazz) {
        Thread currentThread = Thread.currentThread();
        if (emsC > SpaceV2.EM_PURGE_LIMIT) {
            emsC = 0L;
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    ArrayList<Long> tbDelTids = new ArrayList<>();
                    for (Map.Entry<Long, Long> entry : emsT.entrySet()) {
                        Long tid = entry.getKey();
                        Long lasrtSeen = entry.getValue();
                        if (now - lasrtSeen > SpaceV2.EM_PURGE_TIMEOUT) {
                            tbDelTids.add(tid);
                        }
                    }
                    for (Long threadID : tbDelTids) {
                        emsO.remove(threadID);
                        emsT.remove(threadID);
                    }
                }
            })).start();
        }
        if (!emsO.containsKey(currentThread.getId())) {
            emsO.put(currentThread.getId(), EMF.createEntityManager());
            emsC++;
        }
        emsT.put(currentThread.getId(), System.currentTimeMillis());
        return emsO.get(currentThread.getId());
    }
}
