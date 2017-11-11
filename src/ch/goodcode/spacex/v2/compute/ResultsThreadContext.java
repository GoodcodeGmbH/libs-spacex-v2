/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.compute;

import ch.goodcode.spacex.v2.IV2Entity;
import java.util.List;
import javax.persistence.TypedQuery;

/**
 *
 * @author Paolo Domenighetti
 * @param <T>
 */
public class ResultsThreadContext<T extends IV2Entity> {

    public final T singleResult;
    public final TypedQuery<T> queryResult;

    public ResultsThreadContext(T singleResult, TypedQuery<T> queryResult) {
        this.singleResult = singleResult;
        this.queryResult = queryResult;
    }

}
