/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2;

import ch.goodcode.libs.io.EnhancedFilesystemIO;
import ch.goodcode.libs.utils.GOOUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 *
 * @author pdome
 */
public class LogBuffer {

    private final String uid;
    private final String depotFolder;
    private final int capacity;
    private final int loglevel;
    private RandomAccessFile logfile;

    public LogBuffer(String uid, String depotFodler, int capacity, int loglevel) {

        this.uid = uid;
        this.depotFolder = depotFodler;
        this.capacity = capacity;
        this.loglevel = loglevel;

        try {

            File f = new File(depotFolder + "/" + uid + ".txt");
            if (!f.exists()) {
                EnhancedFilesystemIO.textFileWrite(f, new StringBuilder());
            }
            this.logfile = new RandomAccessFile(f, "rw");
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
    }
    
    private static final String SPACE = " ";
    private static final String INFO = " INFO ";
    private static final String WARN = " *WARNING* ";

    private int counter = 0;
    private StringBuilder buffer = new StringBuilder();

    public void s() {
        ss();
    }
    
    private String now() {
        return GOOUtils.getStringedTime_DMYHMSms(System.currentTimeMillis());
    }

    private synchronized void ss() {
        try {
            counter = 0;
            logfile.seek(logfile.length());
            logfile.writeBytes(buffer.toString());
            buffer = new StringBuilder();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    public void i(String msg) {
        if (loglevel > 1) {
            String now = now();
            System.err.println(uid+"@"+now+INFO+msg);
            buffer.append(now).append(INFO).append(msg);
            counter++;
        }
        if (counter >= capacity) {
            ss();
        }
    }

    public void o(String msg) {
        if (loglevel > 0) {
            String now = now();
            System.err.println(uid+"@"+now+SPACE+msg);
            buffer.append(now).append(msg);
            counter++;
        }
        if (counter >= capacity) {
            ss();
        }
    }

    public void e(String msg) {
        String now = now();
        System.err.println(uid+"@"+now+WARN+msg);
        buffer.append(now).append(WARN).append(msg);
        counter++;
        if (counter >= capacity) {
            ss();
        }
    }
    
    public void e(String msg, Exception ex) {
        String now = now();
        System.err.println(uid+"@"+now+WARN+msg+"\n"+ex.getClass().getSimpleName()+" "+ex.getMessage()+"\n"+Arrays.toString(ex.getStackTrace())+"\n");
        buffer.append(now).append(WARN).append(msg).append("\nEXCEPTION:").append(ex.getMessage()).append("\nSTACK:").append(Arrays.toString(ex.getStackTrace()));
        counter++;
        if (counter >= capacity) {
            ss();
        }
    }

    public void c() {
        try {
            logfile.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public int getLoglevel() {
        return loglevel;
    }
    
    
}
