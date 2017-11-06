/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.spacex.v2.SpaceV2;
import java.util.ArrayList;
import java.util.HashMap;
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
    protected final HashMap<String, EntityManager> ems = new HashMap<>();

    public abstract void initialize();
    protected abstract void preDispose();
    protected abstract void postDispose();

    /**
     * 
     */
    public void dispose() {
        preDispose();
        for (Map.Entry<String, EntityManager> entry : ems.entrySet()) {
            EntityManager value = entry.getValue();
            value.clear();
            value.close();
        }
        EMF.close();
    }

    /**
     * 
     * @param <T>
     * @param clazz
     * @return 
     */
    public <T> EntityManager em(Class<T> clazz) {
       return ems.get(clazz.getName());
    }
}
