/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.libs.utils.dataspecs.EJSONObject;

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
}
