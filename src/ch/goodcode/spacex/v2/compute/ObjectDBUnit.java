/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import java.util.HashMap;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 *
 * @author Paolo Domenighetti
 */
public abstract class ObjectDBUnit {

    protected EntityManagerFactory EMF;
    protected final HashMap<String, EntityManager> EMS = new HashMap<>();
    // Attention: each one of this units holds max 10 entity types 
    // 10^6 entities for each type (max. 10^7 entities)
    
    protected final HashMap<String, ObjectDBUnit> KIDS = new HashMap<>();
    protected final HashMap<String, String> BLOCK_MAPPINGS = new HashMap<>();

    public abstract void initialize();
    protected abstract void preDispose();
    protected abstract void postDispose();

    /**
     * 
     */
    public void dispose() {
        KIDS.entrySet().forEach((entry) -> {
            entry.getValue().dispose();
        });
        preDispose();
        EMF.close();
        postDispose();
    }
    
    protected void setKid(String key, ObjectDBUnit u) {
        KIDS.put(key, u);
    }
    
    protected void putEm(String key, EntityManager em) {
        EMS.put(key, em);
    }
    
    protected boolean hasKids() {
        return !KIDS.isEmpty();
    }

    /**
     * The EM returned is already thread protected because it is 
     * used through a ThreadLocal in SpaceV2.
     * @param <T>
     * @param clazz
     * @return 
     */
    public abstract <T> EntityManager em(Class<T> clazz);
}
