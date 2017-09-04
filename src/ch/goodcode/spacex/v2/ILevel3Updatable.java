/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2;

import java.io.Serializable;
import java.util.List;

/**
 *
 * LEVEL 3 API interface
 * @author Paolo
 * @param <T>
 */
public interface ILevel3Updatable<T> extends Serializable {
    
    /**
     * 
     * @param object 
     */
    public void create(T object);
    
    /**
     * 
     * @param object 
     */
    public void update(T object);
    
    /**
     * 
     * @param object 
     */
    public void delete(T object);
    
    /**
     * 
     * @param objects 
     */
    public void create(List<T> objects);
    
    /**
     * 
     * @param objects 
     */
    public void update(List<T> objects);
    
    /**
     * 
     * @param objects 
     */
    public void delete(List<T> objects);
}
