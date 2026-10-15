/*
 * #%L
 * JBossOSGi SPI
 * %%
 * Copyright (C) 2026 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.test.osgi.spi.issues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.BundleException;

/**
 * {@code BundleInfo.createBundleInfo(URL)} and {@code createBundleInfo(String)} mount the archive to read its manifest
 * and throw a {@code BundleException} for an archive that is not a bundle without closing the virtual file they
 * created: the archive stays mounted (an open jar file and a jboss-vfs mount) until JVM exit.
 *
 * The jbosgi-spi part of the framework issue subrepos/07 (AutoInstallInvalidBundleLeaksMountIssueTestCase in the
 * framework itest, where the framework rejects a jar of org.jboss.osgi.auto.install that is not a bundle).
 *
 * Code location: {@code src/main/java/org/jboss/osgi/spi/BundleInfo.java} (createBundleInfo(String),
 * createBundleInfo(URL): {@code new BundleInfo(toVirtualFile(url), ...)} without closing the root when the
 * constructor throws).
 */
public class InvalidBundleInfoLeaksMountIssueTestCase {

    @Test
    public void testInvalidBundleFromURLClosed() throws Exception {
        File file = createJar("bundleinfo-not-a-bundle-url.jar", false);
        try {
            BundleInfo.createBundleInfo(file.toURI().toURL());
            fail("BundleException expected for a jar without manifest");
        } catch (BundleException ex) {
            // expected
        }
        assertNotMounted(file);
    }

    @Test
    public void testInvalidBundleFromLocationClosed() throws Exception {
        File file = createJar("bundleinfo-not-a-bundle-location.jar", false);
        try {
            BundleInfo.createBundleInfo(file.getAbsolutePath());
            fail("BundleException expected for a jar without manifest");
        } catch (BundleException ex) {
            // expected
        }
        assertNotMounted(file);
    }

    @Test
    public void testValidBundleStaysOpen() throws Exception {
        File file = createJar("bundleinfo-bundle.jar", true);
        BundleInfo info = BundleInfo.createBundleInfo(file.toURI().toURL());
        try {
            assertEquals("bundleinfo-bundle", info.getSymbolicName());
            VirtualFile manifest = info.getRoot().getChild("META-INF/MANIFEST.MF");
            assertFalse("The manifest of a valid bundle is accessible", manifest == null);
        } finally {
            info.close();
        }
        assertNotMounted(file);
    }

    private static void assertNotMounted(File file) throws Exception {
        VirtualFile probe = AbstractVFS.toVirtualFile(file.toURI());
        try {
            // isDirectory() does not mount; a jar file is a directory in jboss-vfs only while it is mounted
            assertFalse("The jar " + file + " must not stay mounted (open), but it is a mounted directory in jboss-vfs", probe.isDirectory());
        } finally {
            VFSUtils.safeClose(probe);
        }
    }

    private static File createJar(String name, boolean bundle) throws Exception {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        archive.addAsResource(new StringAsset("content"), "readme.txt");
        if (bundle) {
            archive.setManifest(new Asset() {
                @Override
                public InputStream openStream() {
                    OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                    builder.addBundleManifestVersion(2);
                    builder.addBundleSymbolicName("bundleinfo-bundle");
                    return builder.openStream();
                }
            });
        }
        File file = new File("target/" + name).getAbsoluteFile();
        try (InputStream in = archive.as(ZipExporter.class).exportAsInputStream(); FileOutputStream out = new FileOutputStream(file)) {
            in.transferTo(out);
        }
        return file;
    }
}
