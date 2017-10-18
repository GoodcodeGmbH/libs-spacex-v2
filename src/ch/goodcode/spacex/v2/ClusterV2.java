/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2;

import ch.goodcode.libs.io.EnhancedFilesystemIO;
import ch.goodcode.libs.logging.LogBuffer;
import ch.goodcode.libs.threading.ThreadManager;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import java.io.File;

/**
 *
 * @author Paolo Domenighetti
 */
public final class ClusterV2 {
    
    private Process clusterServerProcess;
    private ThreadManager tmanager;
    private LogBuffer LOG;
    private final EJSONObject clusterConf;
    private int debugRunningPort = 0;
    
    /**
     * 
     * @param uid
     * @param logPath
     * @param logLevel
     * @param clusterConfFilePath 
     */
    public ClusterV2(String uid, String logPath, int logLevel, String clusterConfFilePath) {
        LOG = new LogBuffer("clusterv2-" + uid, logPath, 1, logLevel);
        this.clusterConf = new EJSONObject(EnhancedFilesystemIO.fileRead(new File(clusterConfFilePath)).toString());
    }
    
    /**
     * For DEBUG Constructor
     * 
     * @param uid
     * @param logPath
     * @param logLevel
     * @param runningPort
     * @param dbFoldersForDebug0 
     */
    public ClusterV2(String uid, String logPath, int logLevel, int runningPort, String dbFoldersForDebug0) {
        LOG = new LogBuffer("clusterv2-" + uid, logPath, 1, logLevel);
        this.debugRunningPort = runningPort;
        this.clusterConf = null;
    }
    
    public void start() {
        if(isDebug()) {
            ProcessBuilder p = new ProcessBuilder("");
        } else {
            if(this.clusterConf != null) {
                
            } else {
                // full error
            }
        }
    }
    
    public void stop() {
        
    }
    
    private boolean isDebug() {
        return debugRunningPort != 0;
    }
    
}
