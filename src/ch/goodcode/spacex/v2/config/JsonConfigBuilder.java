/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.config;

import ch.goodcode.libs.io.EnhancedFilesystemIO;
import ch.goodcode.libs.utils.dataspecs.EJSONObject;
import ch.goodcode.spacex.v2.IV2Entity;
import ch.goodcode.spacex.v2.compute.RegVar;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Paolo Domenighetti
 */
public class JsonConfigBuilder {

    private class ConfigHolder {

        public final String clazzname;
        public final int expectedMaxSize;
        public final int optimizationMod;

        public ConfigHolder(String clazzname, int expectedMaxSize, int optimizationMod) {
            this.clazzname = clazzname;
            this.expectedMaxSize = expectedMaxSize;
            this.optimizationMod = optimizationMod;
        }

    }

    private final String uid;
    private final ArrayList<ConfigHolder> holders = new ArrayList<>();

    public JsonConfigBuilder(String uid) {
        this.uid = uid;
        registerClazzType(RegVar.class);
    }

    /**
     *
     * @param clazz
     */
    public final void registerClazzType(Class<? extends IV2Entity> clazz) {
        registerClazzType(clazz, 1, 7);
    }

    /**
     *
     * @param clazz
     * @param expectedMaxSizeInMilions
     * @param optimizationMod
     */
    public final void registerClazzType(Class<? extends IV2Entity> clazz, int expectedMaxSizeInMilions, int optimizationMod) {
        String clazzname = clazz.getName();
        holders.add(new ConfigHolder(clazzname, expectedMaxSizeInMilions, optimizationMod));
    }

    /**
     *
     * @param clazzname
     * @param expectedMaxSize
     * @param optimizationMod
     */
    public final void registerClazzType(String clazzname, int expectedMaxSize, int optimizationMod) {
        holders.add(new ConfigHolder(clazzname, expectedMaxSize, optimizationMod));
    }

    /**
     *
     * @return
     */
    public final EJSONObject compileConfig() {

        final EJSONObject conf = new EJSONObject();

        if (holders.size() > (11 - 1)) {

            int blocks = holders.size() / 9 + 1;
            for (int i = 0; i < blocks; i++) {
                for (int j = 0; j < 9 && (9*i+j) < holders.size(); j++) {
                    ConfigHolder holder = holders.get(9*i+j);
                    if (holder.expectedMaxSize > 1) {
                        // TODO ???
                        // we will have to subchild class ODBEMbeddedUnit
                    } else {
                        switch (holder.optimizationMod) {
                            default:
                                break;
                        }
                    }
                }
            }

        } else {
            for (ConfigHolder holder : holders) {
                if (holder.expectedMaxSize > 1) {
                    // TODO ???
                    // we will have to subchild class ODBEMbeddedUnit
                } else {
                    switch (holder.optimizationMod) {
                        default:
                            break;
                    }
                }
            }
        }

        return conf;

    }

    /**
     *
     * @param outputPath
     */
    public final void compileAndPrintConfigFile(String outputPath) {
        EJSONObject compileConfig = compileConfig();
        EnhancedFilesystemIO.textFileWrite(new File(outputPath + "/" + uid + ".json"), new StringBuilder(compileConfig.toJSONString()));
    }
}
