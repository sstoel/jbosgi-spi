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

import java.util.HashMap;
import java.util.Map;

import org.jboss.osgi.spi.util.StringPropertyReplacer;
import org.jboss.osgi.spi.util.StringPropertyReplacer.PropertyProvider;
import org.junit.Test;

/**
 * {@code StringPropertyReplacer} always uses the default value of a <code>${p:v}</code> reference, also when the
 * property {@code p} is defined: <code>"${bundles.dir:/opt/bundles}/a.jar"</code> becomes {@code "/opt/bundles/a.jar"}
 * whatever {@code bundles.dir} is set to.
 *
 * <h3>Severity</h3>
 * minor. Documented property syntax resolves to the wrong value. Users: jbosgi-framework resolves the
 * {@code org.jboss.osgi.auto.install} / {@code org.jboss.osgi.auto.start} bundle paths with it
 * ({@code BootstrapBundlesInstallPlugin.toURL}), and {@code PropertiesBootstrapProvider} (the bootstrap of
 * jbosgi-testing's {@code OSGiFrameworkTest}) resolves every value of {@code jboss-osgi-framework.properties} with it.
 * Verified at framework level on JDK 25 (exploratory test): with framework properties {@code my.bundles=<dir>} and
 * <code>org.jboss.osgi.auto.install=${my.bundles}/autoinst.jar</code> the framework starts with the bundle installed;
 * with <code>${my.bundles:/does/not/exist}/autoinst.jar</code> {@code Framework.start()} fails with
 * {@code BundleException: JBOSGI011246: Cannot initialize Framework} (the default path is used and does not exist).
 * The WildFly subsystem does not use this class (WildFly resolves its own expressions).
 *
 * <h3>Component</h3>
 * jbosgi-spi, {@code org.jboss.osgi.spi.util.StringPropertyReplacer#replaceProperties(String, PropertyProvider)} (also
 * reached through {@code replaceProperties(String)} and {@code replaceProperties(String, BundleContext)}).
 *
 * <h3>Found by</h3>
 * subrepos-a workstream: code review of the spi utilities used by the framework, then an exploratory table of inputs
 * (<code>${a:def}</code> with a=A gives "def"; <code>${x,a:def}</code> gives "A") and the framework-level check above.
 *
 * <h3>Spec reference</h3>
 * The method's own javadoc ({@code src/main/java/org/jboss/osgi/spi/util/StringPropertyReplacer.java:127-136}):
 * "Go through the input string and replace any occurance of ${p} with the PropertyProvider.getProperty(p) value. ...
 * If the property reference is of the form ${p:v} and there is no such property p, then the default value v will be
 * returned." (the default is only for an undefined property). Same semantics as the JBoss common-core
 * StringPropertyReplacer this class was copied from and as WildFly expressions.
 *
 * <h3>Code location</h3>
 * {@code src/main/java/org/jboss/osgi/spi/util/StringPropertyReplacer.java:196-198} on WF41-osgi-r8:
 * <pre>
 * String realKey = key.substring(0, colon);
 * value = provider.getProperty(key);      // looks up "p:v" again instead of realKey "p"
 * </pre>
 * Introduced with the PropertyProvider variant in b57affe (2010); pre-existing, present on WF41.
 *
 * <h3>Root cause</h3>
 * After the lookup of the whole key "p:v" fails, the code computes {@code realKey = "p"} but looks up {@code key}
 * ("p:v") a second time; then {@code resolveCompositeKey(realKey)} only resolves "p1,p2" forms, so for a plain "p" the
 * default is returned.
 *
 * <h3>Reproduction</h3>
 * <pre>
 * cd jbosgi-spi
 * export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
 * mvn -Dmaven.compiler.proc=full test -Dtest=PropertyDefaultValueOverridesIssueTestCase
 * </pre>
 *
 * <h3>Observed</h3>
 * <pre>
 * testDefinedPropertyWithDefault: A defined property must win over the default value in ${p:v} (bundles.dir=/srv/bundles)
 *   expected:&lt;/srv/bundles/a.jar&gt; but was:&lt;/opt/bundles/a.jar&gt;
 * testSystemPropertyWithDefault: ... expected:&lt;[defined]&gt; but was:&lt;[fallback]&gt;
 * </pre>
 *
 * <h3>Expected</h3>
 * <code>${p:v}</code> yields the value of {@code p} when {@code p} is defined and {@code v} only otherwise.
 *
 * <h3>Reference behaviour</h3>
 * JBoss common-core {@code org.jboss.util.StringPropertyReplacer} and WildFly expression resolution return the defined
 * value.
 *
 * <h3>Suggested fix</h3>
 * {@code value = provider.getProperty(realKey);} at line 198.
 *
 * <h3>Regression vs WF41</h3>
 * no (pre-existing).
 */
public class PropertyDefaultValueOverridesIssueTestCase {

    @Test
    public void testDefinedPropertyWithDefault() {
        final Map<String, String> props = new HashMap<String, String>();
        props.put("bundles.dir", "/srv/bundles");
        PropertyProvider provider = new PropertyProvider() {
            @Override
            public String getProperty(String key) {
                return props.get(key);
            }
        };
        if (!"/srv/bundles/a.jar".equals(StringPropertyReplacer.replaceProperties("${bundles.dir}/a.jar", provider)))
            throw new IllegalStateException("Precondition: ${bundles.dir} resolves without a default");
        assertEquals("A defined property must win over the default value in ${p:v} (bundles.dir=/srv/bundles)", "/srv/bundles/a.jar",
                StringPropertyReplacer.replaceProperties("${bundles.dir:/opt/bundles}/a.jar", provider));
        assertEquals("The default value is used for an undefined property", "/opt/other/a.jar",
                StringPropertyReplacer.replaceProperties("${other.dir:/opt/other}/a.jar", provider));
    }

    @Test
    public void testSystemPropertyWithDefault() {
        String key = PropertyDefaultValueOverridesIssueTestCase.class.getName() + ".prop";
        System.setProperty(key, "defined");
        try {
            assertEquals("A defined system property must win over the default value in ${" + key + ":fallback}", "defined",
                    StringPropertyReplacer.replaceProperties("${" + key + ":fallback}"));
        } finally {
            System.clearProperty(key);
        }
    }
}
