/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.Persistence;

/**
 *
 * @author Paolo Domenighetti
 */
public class ODBEmbeddedUnit extends ObjectDBUnit {

    private final boolean debug;
    private final String optHostFull;
    private final String optUser, optPass;

    /**
     * FOR DEBUG
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
     * FOR DEBUG
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
     */
    public ODBEmbeddedUnit(EJSONObject unitConf) {
        this.optHostFull = null;
        this.optUser = null;
        this.optPass = null;
        this.debug = false;
    }
    
    

    @Override
    public void initialize() {
        if (debug) {
            if (optUser == null) {
                EMF = Persistence.createEntityManagerFactory(optHostFull);
            } else {
                Map<String, String> properties = new HashMap<>();
                properties.put("javax.persistence.jdbc.user", optUser);
                properties.put("javax.persistence.jdbc.password", optPass);
                EMF = Persistence.createEntityManagerFactory(optHostFull, properties);
            }
        } else {

        }

    }

    @Override
    protected void preDispose() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
