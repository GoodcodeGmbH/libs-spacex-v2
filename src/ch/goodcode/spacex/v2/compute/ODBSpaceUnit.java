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
public class ODBSpaceUnit extends ODBStandaloneUnit {
    
    public ODBSpaceUnit(EJSONObject unitConf) {
        super(unitConf);
    }

    @Override
    protected void preDispose() {
        super.preDispose(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void initialize() {
        super.initialize(); //To change body of generated methods, choose Tools | Templates.
    }
    
}