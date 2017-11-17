/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;

/**
 *
 * @author Paolo Domenighetti
 */
public class ODBEmbeddedUnit extends ObjectDBUnit {

    private final boolean debug;
    private final String optHostFull;
    private final String optUser, optPass;
    private final EJSONObject config;

    /**
     * *** FOR DEBUG ***
     *
     * @param optHostFull
     */
    public ODBEmbeddedUnit(String optHostFull) {
        this.optHostFull = optHostFull;
        this.optUser = null;
        this.optPass = null;
        this.debug = true;
        this.config = null;
    }

    /**
     * *** FOR DEBUG ***
     *
     * @param optHostFull
     * @param optUser
     * @param optPass
     */
    public ODBEmbeddedUnit(String optHostFull, String optUser, String optPass) {
        this.optHostFull = optHostFull;
        this.optUser = optUser;
        this.optPass = optPass;
        this.debug = true;
        this.config = null;
    }

    /**
     *
     * @param unitConf
     * @throws java.lang.Exception
     */
    public ODBEmbeddedUnit(EJSONObject unitConf) throws Exception {
        this.optHostFull = null;
        this.optUser = unitConf.getString("user");
        this.optPass = unitConf.getString("password");
        this.debug = false;
        this.config = unitConf;
    }

    @Override
    public void initialize() {
        if (debug) {
            if (optUser == null) {
                // a single default embedded EMF with odb file
                EMF = Persistence.createEntityManagerFactory(optHostFull);
            } else {
                // this is exceptional if optHostfull is a network location, embedded unit shall never be initialized
                // as net services on a local port, but only with odb files instead.
                // Here it is for networking+performance testing purposes.
                Map<String, String> properties = new HashMap<>();
                properties.put("javax.persistence.jdbc.user", optUser);
                properties.put("javax.persistence.jdbc.password", optPass);
                EMF = Persistence.createEntityManagerFactory(optHostFull, properties);
            }
        } else {
            // the main emf is used for regvars, and other ev. meta entities
            // also properties of the emf tree. The config file contains max items
            // per type so we can pre-compute the tree structure.
            Map<String, String> properties = new HashMap<>();
            properties.put("javax.persistence.jdbc.user", optUser);
            properties.put("javax.persistence.jdbc.password", optPass);
            
            // TODO ...
            // ...
            
        }

    }

    @Override
    protected void preDispose() {

    }

    @Override
    protected void postDispose() {

    }

    @Override
    public <T> EntityManager em(Class<T> clazz) {
        return null;
    }

}
