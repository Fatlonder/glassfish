/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.enterprise.deployment.deploy.shared;

import com.sun.enterprise.util.io.FileUtils;

import org.glassfish.api.deployment.archive.ReadableArchive;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.JarEntry;
import java.util.zip.ZipEntry;

/**
 * This Archive offers an abstraction for jsr88
 * deployment plan as defined for the SJES Application 
 * Server. 
 *
 * @author Jerome Dochez
 */
public class DeploymentPlanArchive extends JarArchive implements ReadableArchive {

    // the deployment plan jar file...
    JarFile jarFile = null;
    
    // original archive uri
    URI uri;
    
    // cached list of elements
    Vector elements=null;
    
    String subArchiveUri=null;
    
    /** Creates a new instance of DeploymentPlanArchive 
     * package private
     */
    public DeploymentPlanArchive() {
    }
    
    /** Open an existing DeploymentPlan archive and return 
     * a abstraction for reading from it.
     * @param uri the path to the archive
     */
    public void open(URI uri) throws IOException {
        this.uri = uri;
        File f = new File(uri);
        if (f.exists()) {
            jarFile = new JarFile(f);
        }
    }
    
    /**
     * Get the size of the archive
     * @return tje the size of this archive or -1 on error
     */
    public long getArchiveSize() throws NullPointerException, SecurityException {
        if(uri == null) {
            return -1;
        }
        File tmpFile = new File(uri);
        return(tmpFile.length());
    }
    
    /**
     * Closes the current jar file
     */
    public void close() throws java.io.IOException {
        if (jarFile!=null) {
            jarFile.close();
            jarFile=null;
        }
    }
    
    /**
     * Closes the output jar file entry
     */
    public void closeEntry() throws java.io.IOException {
        // nothing to do
    }
    
    /**
     * Closes the output sub archive entry
     */
    public void closeEntry(ReadableArchive sub) throws java.io.IOException {
        // nothing to do...
    }
    
    /**
     * Deletes the underlying jar file
     */
    public boolean delete() {
        File f = new File(uri);
        if (f.exists()) {
            return FileUtils.deleteFile(f);
        }
        return false;
    }
    
    /**
     * @return an Enumeration of entries for this archive
     */
    public Enumeration entries() {
        // Deployment Plan are organized flatly, 
        
        if (elements==null) {
            synchronized(this) {
                elements = new Vector();
                for (Enumeration e = jarFile.entries();e.hasMoreElements();) {
                    ZipEntry ze = (ZipEntry) e.nextElement();
                    if (!ze.isDirectory() && !ze.getName().equals(
                            JarFile.MANIFEST_NAME)) {
                        elements.add(ze.getName());
                    }
                }
            }
        }

        Vector entries = new Vector();
        for (Enumeration e = elements.elements();e.hasMoreElements();) {
            
            String entryName = (String) e.nextElement();
            
            String mangledName = entryName;
            String prefix = "META-INF/";
            if (entryName.indexOf("sun-web.xml")!=-1) {
                prefix = "WEB-INF/";
            }  
            if (subArchiveUri != null && entryName.startsWith(subArchiveUri)) {
                mangledName = mangledName.substring(subArchiveUri.length()+1);
            }
            if (entryName.endsWith(".dbschema")) {
                mangledName = mangledName.replaceAll("#", "/");
            } else {
                mangledName = prefix + mangledName;
            }
            
            if (subArchiveUri==null) {
                // top level archive
                if ((entryName.indexOf(".jar.")!=-1) || 
                    (entryName.indexOf(".war.")!=-1) || 
                    (entryName.indexOf(".rar."))!=-1) {
                    
                    // this element is in a sub archive
                    continue;
                }
                entries.add(mangledName);            
            } else {
                // this is a sub archive
                if (entryName.startsWith(subArchiveUri)) {
                    entries.add(mangledName);
                }
            }             
        } 
        return entries.elements();
    }
    
    /**
     * @return an Enumeration of entries not including entries 
     * from the subarchives
     */
    public Enumeration entries(java.util.Enumeration embeddedArchives) {
        return entries();
    }
    
    /**
     * @return true if the underlying archive exists
     */
    public boolean exists() {
        File f = new File(uri);
        return f.exists();
    }
    
    /**
     * @return a sub archive giving the name 
     */
    public ReadableArchive getSubArchive(String name) throws java.io.IOException {
        if (jarFile==null) {
            return null;
        }
        DeploymentPlanArchive dpArchive = new DeploymentPlanArchive();
        dpArchive.jarFile = new JarFile(new File(uri));
        try {
            dpArchive.uri = new URI("file", uri.getSchemeSpecificPart() + File.separator + name, null);
        } catch (URISyntaxException e) {
            //
        }
        dpArchive.subArchiveUri = name;
        dpArchive.elements = elements;
        return dpArchive;        
    }
    
    /**
     * @return an input stream giving its entry name
     */
    public InputStream getEntry(String name) throws IOException {

        // we are just interested in the file name, not the 
        // relative path
        if (name.endsWith(".dbschema")) {
            name = name.replaceAll("/", "#");
        } else {
            name = name.substring(name.lastIndexOf('/')+1);
        }
        
        if (subArchiveUri==null) {
            // we are at the "top level"
            
            return getElement(name);
        } else {
            // we are in a sub archive...
            // now let's mangle the name...
            String mangledName = subArchiveUri + "." + 
                name;
            return getElement(mangledName);
            
        }        
    }

    /**
     * Returns the entry size for a given entry name or 0 if not known
     *
     * @param name the entry name
     * @return the entry size
     */
    public long getEntrySize(String name) {
        if (elements.contains(name)) {
            ZipEntry je = jarFile.getEntry(name);
            return je.getSize();
        } else {
            return 0;
        }
    }

    private InputStream getElement(String name) throws IOException {
        if (elements.contains(name)) {
            return jarFile.getInputStream(jarFile.getEntry(name));
        } else {
            return null;
        }
    }
    
    /** 
     * @return the manifest
     */
    public java.util.jar.Manifest getManifest() throws java.io.IOException {
        // no manifest in DeploymentPlan
        return new Manifest();
    }

    /**
     * Returns the path used to create or open the underlying archive
     *
     * @return the path for this archive.
     */
    public URI getURI() {
        return uri;
    }

    /**
     * rename the underlying archive
     */
    public boolean renameTo(String name) {
        File f = new File(uri);
        File to  = new File(name);
        boolean result = FileUtils.renameFile(f, to);
        if (result) {
            uri = to.toURI();
        }
        return result;
        
    }
    
}
