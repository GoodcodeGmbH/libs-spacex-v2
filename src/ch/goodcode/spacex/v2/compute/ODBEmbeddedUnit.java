/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.libs.utils.dataspecs.EJSONArray;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;

/**
 *
 * @author Paolo Domenighetti
 */
public final class ODBEmbeddedUnit extends ObjectDBUnit {

    private static final String DEBUG_KEY = "DEBUG";

    private final boolean debug;
    private final String optHostFull;
    private final String optUser, optPass;

    // multiblock params
    private int blocks = 1;
    private final HashMap<String, String> BLOCK_MAPPINGS = new HashMap<>();

    protected ODBEmbeddedUnit() {
        this.optHostFull = null;
        this.optUser = null;
        this.optPass = null;
        this.debug = true;
    }

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

        // TODO meta entities handling/setup
        // ...
        // entities setup (virtual tables)
        EJSONArray array = unitConf.getArray("types");
        int size = array.size();
        if (size < (11 - 1)) {
            for (int i = 0; i < size; i++) {
                EJSONObject object = array.getObject(i);
                boolean segmented = object.getBoolean("segmented");
                if (!segmented) {
                    EMS.put(object.getString("clazz"), null);
                } else {
                    // TODO (means > 10^6 items / table)
                    // initially 2 layers are built (10^(6+2) items / virtual table)

                }
            }
        } else {
            // TODO
            blocks = (size / 10) + 1; // this is always >= 2
            for (int i = 0; i < blocks; i++) {
                ODBEmbeddedUnit u = new ODBEmbeddedUnit(optHostFull, optUser, optPass);
                // TODO...
            }
            int r = (size % 10) + 1;
        }
    }

    @Override
    public void initialize() {
        if (debug) {
            if (optUser == null) {
                // a single default embedded EMF with odb file
                // without credentials (attention).
                EMF = Persistence.createEntityManagerFactory(optHostFull);
                EMS.put(DEBUG_KEY, EMF.createEntityManager());
            } else {
                // this is exceptional if optHostfull is a network location, embedded unit shall never be initialized
                // as net services on a local port, but only with odb files instead.
                // Here it is for networking+performance testing purposes.
                Map<String, String> properties = new HashMap<>();
                properties.put("javax.persistence.jdbc.user", optUser);
                properties.put("javax.persistence.jdbc.password", optPass);
                EMF = Persistence.createEntityManagerFactory(optHostFull, properties);
                EMS.put(DEBUG_KEY, EMF.createEntityManager());
            }
        } else {
            // the main emf is used for regvars, and other ev. meta entities
            // also properties of the emf tree. The config file contains max items
            // per type so we can pre-compute the tree structure.

            if (blocks > 1) {

                for (int i = 0; i < blocks; i++) {
                    ODBEmbeddedUnit u = new ODBEmbeddedUnit();
                    KIDS.put("block" + i, u);
                }

            } else {
                Map<String, String> properties = new HashMap<>();
                properties.put("javax.persistence.jdbc.user", optUser);
                properties.put("javax.persistence.jdbc.password", optPass);
                EMS.entrySet().stream().map((entry) -> entry.getKey()).forEachOrdered((key) -> {
                    EMS.put(key, EMF.createEntityManager());
                });
            }
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
        if (debug) {
            return EMS.get(DEBUG_KEY);
        } else {
            if (blocks > 1) {
                final String kidKey = BLOCK_MAPPINGS.get(clazz.getName());
                return KIDS.get(kidKey).em(clazz);
            } else {
                return EMS.get(clazz.getName());
            }
        }
    }

    public boolean isDebug() {
        return debug;
    }

}
