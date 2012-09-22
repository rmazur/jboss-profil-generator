package org.redhat.jboss.maven.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.IllegalSelectorException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Goal which touches a timestamp file.
 * 
 * @goal jboss-profile
 * 
 * @phase process-sources
 */
public class JBossProfileGeneratorMojo extends AbstractMojo {

    public static final String FILE_SEPARATOR = "/";
    /**
     * Directory to move the undeployed part of JBoss AS - relative to JBoss
     * HOME when relative
     * 
     * @parameter
     */
    private File undeployedDirectory;

    /**
     * JBOSS HOME
     * 
     * @parameter
     * @required
     */
    private File jbossHome;

    /**
     * Source profile name
     * 
     * @parameter
     */
    private String sourceProfileName = "default";

    /**
     * Target profile name
     * 
     * @parameter
     * @required
     */
    private String targetProfileName;

    private Set<String> servicesToRemove;

    /**
     * Extra files to deploy (Queue, Datasources,...)
     * 
     * @parameter
     */
    private Set<File> filesToDeploy;

    public static void main(String[] args) {
        String jbossHome = "/home/rpelisse/Products/products-dist.git/jboss-as/";
        String hornetqInstallFolder = "extras/hornetq/";
        String hornetInstallAntFile = jbossHome + hornetqInstallFolder + "build.xml";

        new JBossProfileGeneratorMojo().executeCmd("ant -f " + hornetInstallAntFile + " -Djboss_home=" + jbossHome);
    }

    private boolean replaceJBossMessagingByHornetQ() {
        final String hornetInstallAntFile = jbossHome + FILE_SEPARATOR + 
                                            "extras/hornetq" + FILE_SEPARATOR + 
                                            "build.xml";        
        return executeCmd("ant -f " + hornetInstallAntFile + " -Djboss_home=" + jbossHome);
    }
    
    public boolean executeCmd(String command) {

        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();
            IOUtils.copy(process.getInputStream(), System.out);

            return process.exitValue() == 0 ? true
                    : false;
        } catch (Exception e) {
            getLog().error(e.getMessage());
            return false;
        }
    }

    /**
     * Default constructor
     */
    public JBossProfileGeneratorMojo() {}

    private void undeployService(String serviceName) {
        getLog().info("Undeploying service:" + serviceName);
        moveServiceToUndeployed(locateService(serviceName), undeployedDirectory);
    }

    private void moveServiceToUndeployed(File service, File undeployedDirectory) {
        if (!undeployedDirectory.exists())
            undeployedDirectory.mkdirs();
        service.renameTo(undeployedDirectory);
    }

    private File locateService(String serviceName) {
        File file = getProfilesDirFile(this.targetProfileName + FILE_SEPARATOR 
                + "deploy" + FILE_SEPARATOR + serviceName);
        if (file.exists())
            return file;
        throw new IllegalStateException("File does not exist:" + file);
    }

    public void execute() throws MojoExecutionException {
        init();
        File targetProfile = getProfilesDirFile(targetProfileName);
        // File(targetProfile);
        if (targetProfile.exists() && targetProfile.isDirectory() ) {
            getLog().info(
                    "Target profile already exist (" + this.targetProfileName
                    + "... Skipping");
            return;
        } 
        
        replaceJBossMessagingByHornetQ();
        createProfile(sourceProfileName,targetProfile);
        for (String serviceName : this.servicesToRemove)
            undeployService(serviceName);
        deployFiles();
    }
    
    private void deployFiles() {
        if ( filesToDeploy != null && ! filesToDeploy.isEmpty() ) {
            JBossProfileGeneratorMojo.copyFilesTo(filesToDeploy, new File(targetProfileName + FILE_SEPARATOR + "deploy" + FILE_SEPARATOR));
        }
    }

    private static void copyFilesTo(Set<File> files, File targetDir) {
        try {
            for (File file : files) {
                if ( ! file.exists() )
                    throw new IllegalArgumentException("File " + file.getAbsoluteFile() + " does not exist, hence can't be copy to " + targetDir.getAbsoluteFile());
    
                    IOUtils.copy(new FileInputStream(file),new FileOutputStream(targetDir));
            }
        } catch (IOException e) {
           throw new IllegalArgumentException(e);
        }
    }
    private static boolean invalidTargetProfileName(String profileName) {
        // TODO: check against existing profile
        return true;
    }
    
    private void init() {
        if ( jbossHome == null || ! jbossHome.exists() )
            throw new IllegalStateException("JBOSS_HOME provided " + jbossHome.getAbsoluteFile() + " does not exists.");
        File profileDir = getProfilesDir();
        if ( profileDir == null || ! profileDir.exists() )
            throw new IllegalStateException("JBOSS HOME provided " + jbossHome.getAbsoluteFile() + " is missing the expected 'server' folder for profile. Remark: plugin only works for JBoss AS prior to AS 7");
        if ( targetProfileName == null || "".equals(targetProfileName) )
            throw new IllegalStateException("Missing target profile name.");
        if ( ! JBossProfileGeneratorMojo.invalidTargetProfileName(targetProfileName))
            throw new IllegalArgumentException("Profile name " + targetProfileName + " is not valid.");
        // Populate default remove services list
        if ( this.servicesToRemove == null || this.servicesToRemove.isEmpty() ) {
            getLog().debug("Services to remove list is empty, filling with default one.");
            this.servicesToRemove = new HashSet<String>();
            this.servicesToRemove.add("cache-invalidation-service.xml");
            this.servicesToRemove.add("ejb2-container-jboss-beans.xml");
            this.servicesToRemove.add("ejb2-timer-service.xml");
            /*
             * this.servicesToRemove.add("ejb3-connectors-jboss-beans.xml");
             * this.servicesToRemove.add("ejb3-container-jboss-beans.xml");
             * this.servicesToRemove.add("ejb3-interceptors-aop.xml");
             * this.servicesToRemove.add("ejb3-timerservice-jboss-beans.xml");
             */
            this.servicesToRemove.add("jsr88-service.xml");
        }
        // Defautl undeployed dir
        if ( undeployedDirectory == null ) {
            undeployedDirectory =  getProfilesDirFile(targetProfileName);
        }
    }
    
    private File getProfilesDir() {
        return getProfilesDirFile("");
    }

    private File getProfilesDirFile(String relativePathToFile) {
        return new File(jbossHome.getAbsoluteFile() + FILE_SEPARATOR + "server" + FILE_SEPARATOR + relativePathToFile);        
    }

    private void createProfile(String sourceProfileName,
            File targetProfile) {
        File sourceProfile = getProfilesDirFile(sourceProfileName);
        if (sourceProfile == null || ! sourceProfile.exists())
            throw new IllegalStateException("Source profile does not exists:" + sourceProfile);
        if (! sourceProfile.isDirectory())
            throw new IllegalArgumentException("Source profile "
                    + sourceProfileName + " does not points to a folder !");

        try {
            FileUtils.copyDirectory(sourceProfile, targetProfile);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

    }

}
