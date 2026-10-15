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

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.jboss.osgi.spi.util.ServiceLoader;
import org.junit.Test;

/**
 * {@code org.jboss.osgi.spi.util.ServiceLoader.loadServices} reads only the first
 * {@code META-INF/services/<service>} file visible to the class loader; providers declared by any other jar or
 * directory on the class path are silently missing from the returned "list of available services".
 *
 * <h3>Severity</h3>
 * minor. Users: {@code OSGiBootstrap.getBootstrapProvider()} iterates {@code loadServices(OSGiBootstrapProvider.class)}
 * to find the first provider that configures successfully, so a working provider in a second jar is never tried; the
 * many {@code loadService(FrameworkFactory.class)} callers (jbosgi-testing {@code OSGiFrameworkTest} through
 * {@code PropertiesBootstrapProvider}, framework tests) are not affected because they only take the first provider,
 * which is the same as with {@code java.util.ServiceLoader}.
 *
 * <h3>Component</h3>
 * jbosgi-spi, {@code org.jboss.osgi.spi.util.ServiceLoader#loadServices(Class)}.
 *
 * <h3>Found by</h3>
 * subrepos-a workstream, differential test against {@code java.util.ServiceLoader} (two class path roots that both
 * declare providers: JDK returns [Impl1, Impl2], jbosgi-spi returns [Impl1]).
 *
 * <h3>Spec reference</h3>
 * The method's javadoc: "Loads a list of service implementations defined in META-INF/services/${serviceClass} ...
 * &#64;return The list of available service or an empty list", and its implementation comment "Use the Services API (as
 * detailed in the JAR specification)". JAR File Specification, Service Provider / {@code java.util.ServiceLoader}
 * (Java SE 25): "A service provider that is packaged as a JAR file for the class path is identified by placing a
 * provider-configuration file in the resource directory META-INF/services." and, for
 * {@code ServiceLoader.load(Class, ClassLoader)}, "Step 2: Locate providers in unnamed modules": "Service providers
 * in unnamed modules are located if their class names are listed in provider-configuration files located by the
 * class loader's getResources method." and "The ordering is based on the order that the class loader's getResources
 * method finds the service configuration files and within that, the order that the class names are listed in the
 * file." (that is, all of them, in class path order).
 * https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html
 *
 * <h3>Code location</h3>
 * {@code src/main/java/org/jboss/osgi/spi/util/ServiceLoader.java:94-96}:
 * {@code InputStream inStream = loader.getResourceAsStream(filename);} (one resource only). Pre-existing since 2009
 * (b8ba3cd), present on WF41.
 *
 * <h3>Root cause</h3>
 * {@code getResourceAsStream} returns the first matching resource; the loader should iterate
 * {@code getResources(filename)}.
 *
 * <h3>Reproduction</h3>
 * <pre>
 * cd jbosgi-spi
 * export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
 * mvn -Dmaven.compiler.proc=full test -Dtest=ServiceLoaderFirstProviderFileOnlyIssueTestCase
 * </pre>
 *
 * <h3>Observed</h3>
 * <pre>
 * testProvidersOfAllClassPathRoots: loadServices must return the providers declared in every META-INF/services file,
 *   like java.util.ServiceLoader expected:&lt;[...$FirstProvider, ...$SecondProvider]&gt; but was:&lt;[...$FirstProvider]&gt;
 * </pre>
 *
 * <h3>Expected</h3>
 * The providers of all provider-configuration files, in class path order (the same list as
 * {@code java.util.ServiceLoader}).
 *
 * <h3>Reference behaviour</h3>
 * {@code java.util.ServiceLoader.load(service, loader)} on JDK 25 returns both providers (asserted in the test as the
 * reference).
 *
 * <h3>Suggested fix</h3>
 * {@code Enumeration<URL> urls = loader.getResources(filename);} and read each file (skipping duplicate class names);
 * close each reader in a finally block.
 *
 * <h3>Regression vs WF41</h3>
 * no (pre-existing).
 */
public class ServiceLoaderFirstProviderFileOnlyIssueTestCase {

    public interface ExampleService {
    }

    public static class FirstProvider implements ExampleService {
    }

    public static class SecondProvider implements ExampleService {
    }

    @Test
    public void testProvidersOfAllClassPathRoots() throws Exception {
        File first = createRoot("first", FirstProvider.class);
        File second = createRoot("second", SecondProvider.class);
        URL testClasses = ServiceLoaderFirstProviderFileOnlyIssueTestCase.class.getProtectionDomain().getCodeSource().getLocation();
        URLClassLoader loader = new URLClassLoader(new URL[] { first.toURI().toURL(), second.toURI().toURL(), testClasses }, null);
        try {
            Class<?> service = loader.loadClass(ExampleService.class.getName());

            List<String> reference = new ArrayList<String>();
            for (Object provider : java.util.ServiceLoader.load(service, loader))
                reference.add(provider.getClass().getName());
            if (reference.size() != 2)
                throw new IllegalStateException("Precondition: java.util.ServiceLoader finds both providers, but found " + reference);

            List<String> actual = new ArrayList<String>();
            for (Object provider : ServiceLoader.loadServices(service))
                actual.add(provider.getClass().getName());
            assertEquals("loadServices must return the providers declared in every META-INF/services file, like java.util.ServiceLoader", reference, actual);
        } finally {
            loader.close();
        }
    }

    private static File createRoot(String name, Class<?> provider) throws Exception {
        File root = Files.createTempDirectory(new File("target").toPath(), "serviceloader-" + name).toFile();
        File services = new File(root, "META-INF/services");
        services.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(services, ExampleService.class.getName()))) {
            out.write((provider.getName() + "\n").getBytes("UTF-8"));
        }
        return root;
    }
}
