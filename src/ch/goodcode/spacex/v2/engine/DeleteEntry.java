/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 *
 * @author pdome
 */
@Entity
public class DeleteEntry implements Serializable {

    public static final long serialVersionUID = UpdateEntry.class.getCanonicalName().hashCode();

    @Id
    private String uid;
    private String clazzname;
    private String target;
    private String peer;
    private long lastDeleted;

    public String getClazzname() {
        return clazzname;
    }

    public void setClazzname(String clazzname) {
        this.clazzname = clazzname;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getPeer() {
        return peer;
    }

    public void setPeer(String peer) {
        this.peer = peer;
    }

    public long getLastDeleted() {
        return lastDeleted;
    }

    public void setLastDeleted(long lastUpdated) {
        this.lastDeleted = lastUpdated;
    }
}
