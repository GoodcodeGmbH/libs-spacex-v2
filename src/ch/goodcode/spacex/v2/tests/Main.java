/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.tests;

import ch.goodcode.libs.security.EnhancedCryptography;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import javax.persistence.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKey;

public class Main {

    public static void main(String[] args) {
        
        try {
            SecretKey k = EnhancedCryptography.generateSecretKey();
            String saveSecretKey = EnhancedCryptography.saveSecretKey(k);
            System.err.println(saveSecretKey);
//        // Open a database connection
//        // (create a new database if it doesn't exist yet):
//        EntityManagerFactory emf
//                = Persistence.createEntityManagerFactory("$objectdb/db/points.odb");
//        EntityManager em = emf.createEntityManager();
//
//        // Store 1000 Point objects in the database:
//        em.getTransaction().begin();
//        for (int i = 0; i < 1000; i++) {
//            MyPoint p = new MyPoint(i, i);
//            em.persist(p);
//        }
//        em.getTransaction().commit();
//
//        // Find the number of Point objects in the database:
//        Query q1 = em.createQuery("SELECT COUNT(p) FROM MyPoint p");
//        System.out.println("Total Points: " + q1.getSingleResult());
//
//        // Find the average X value:
//        Query q2 = em.createQuery("SELECT AVG(p.x) FROM MyPoint p");
//        System.out.println("Average X: " + q2.getSingleResult());
//
//        // Retrieve all the Point objects from the database:
//        TypedQuery<MyPoint> query
//                = em.createQuery("SELECT p FROM MyPoint p", MyPoint.class);
//        List<MyPoint> results = query.getResultList();
//        for (MyPoint p : results) {
//            System.out.println(p);
//        }
//
//        // Close the database connection:
//        em.close();
//        emf.close();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchProviderException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
