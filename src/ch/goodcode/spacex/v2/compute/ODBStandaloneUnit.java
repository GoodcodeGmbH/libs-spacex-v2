/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import javax.persistence.EntityManager;

/**
 *
 * @author Paolo Domenighetti
 */
public class ODBStandaloneUnit extends ObjectDBUnit {
    
    private Process standaloneObjectDB;

    /**
     * 
     * @param unitConf 
     */
    public ODBStandaloneUnit(EJSONObject unitConf) {
    }


    @Override
    public void initialize() {
        // standalone unit may only be initialized as
        // web services using a local or remote hotst:port.
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void preDispose() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void postDispose() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> EntityManager em(Class<T> clazz) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
