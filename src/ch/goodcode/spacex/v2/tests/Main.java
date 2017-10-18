/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.tests;

import ch.goodcode.libs.logging.LogBuffer;
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
            final SpaceV2Debug mem = new SpaceV2Debug("", "C:\\temp\\space-test\\log\\", LogBuffer.LOGLEVEL_PEDANTIC, "C:\\temp\\space-test\\mem.odb");
            mem.start();
            
            Author a = new Author();
            a.setName("Paolo");
            a.setRating(32);
            
            EBook b = new EBook();
            b.setAuthor(a);
            b.setPrice(2500);
            b.setTitle("A title");
            b.setPreview("Lorem ipsum...");
            
            mem.create(b);
            
            List<EBook> findAll_MATCH = mem.findAll_MATCH(EBook.class, "title", "A title", 0);
            if(!findAll_MATCH.isEmpty()) {
                b = findAll_MATCH.get(0);
            }
            
            System.err.println(b.toString());
            
            b.setTitle("Another title");
            mem.update(b);
            
            findAll_MATCH = mem.findAll_LIKE(EBook.class, "title", "title", 0);
            if(!findAll_MATCH.isEmpty()) {
                b = findAll_MATCH.get(0);
            }
            
            System.err.println(b.toString());
            
            mem.stop();
        } catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
