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

import org.jboss.osgi.spi.ConstantsHelper;
import org.junit.Test;
import org.osgi.framework.FrameworkEvent;

/**
 * {@code ConstantsHelper.frameworkEvent} does not know the R7 event type {@code FrameworkEvent.STOPPED_SYSTEM_REFRESHED}
 * and renders it as "[1024]", although the R8 framework fires it.
 *
 * <h3>Severity</h3>
 * cosmetic. Only names in log/diagnostic output are affected. The helper is the framework's and the test kit's way to
 * name event types (jbosgi-framework {@code FrameworkEventsImpl:485, :921}; jbosgi-testing {@code OSGiFrameworkTest}
 * listener logging and {@code assertFrameworkEvent}, which compares names). Since the R7/R8 work the framework stops
 * with {@code STOPPED_SYSTEM_REFRESHED} after a framework extension refresh ({@code FrameworkWiringImpl:115}); that stop
 * event is currently created directly ({@code BundleManagerPlugin:1006}) and not formatted by the helper, so no
 * framework log line is known to show "[1024]" today; any caller that names it does.
 *
 * <h3>Component</h3>
 * jbosgi-spi, {@code org.jboss.osgi.spi.ConstantsHelper#frameworkEvent(int)}.
 *
 * <h3>Found by</h3>
 * subrepos-a workstream, check of the spi helpers against the OSGi Core R8 constants (all FrameworkEvent types listed
 * in {@code org.osgi.framework.FrameworkEvent} of osgi.core 8.0.0).
 *
 * <h3>Spec reference</h3>
 * OSGi Core R8 API, {@code org.osgi.framework.FrameworkEvent.STOPPED_SYSTEM_REFRESHED} (0x00000400, "@since 1.9", i.e.
 * Core R7): "The Framework has stopped and the framework requires a new class loader to restart. This event is fired when
 * the Framework has been stopped because of a refresh operation on the system bundle and the framework requires a new
 * class loader to be used to restart." https://docs.osgi.org/javadoc/osgi.core/8.0.0/org/osgi/framework/FrameworkEvent.html
 *
 * <h3>Code location</h3>
 * {@code src/main/java/org/jboss/osgi/spi/ConstantsHelper.java:122-146}: the if/else chain ends with
 * {@code WAIT_TIMEDOUT} and {@code WARNING}; there is no branch for {@code STOPPED_SYSTEM_REFRESHED}. The R7/R8 commits
 * of jbosgi-spi (65b1324, 793a33f) only changed the pom.
 *
 * <h3>Root cause</h3>
 * The helper was not updated when the build moved to OSGi Core R7/R8.
 *
 * <h3>Reproduction</h3>
 * <pre>
 * cd jbosgi-spi
 * export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
 * mvn -Dmaven.compiler.proc=full test -Dtest=FrameworkEventNameSystemRefreshedIssueTestCase
 * </pre>
 *
 * <h3>Observed</h3>
 * <pre>
 * testStoppedSystemRefreshed: FrameworkEvent.STOPPED_SYSTEM_REFRESHED must have a name
 *   expected:&lt;[STOPPED_SYSTEM_REFRESHED]&gt; but was:&lt;[[1024]]&gt;
 * </pre>
 *
 * <h3>Expected</h3>
 * "STOPPED_SYSTEM_REFRESHED".
 *
 * <h3>Reference behaviour</h3>
 * n/a.
 *
 * <h3>Suggested fix</h3>
 * Add {@code else if (FrameworkEvent.STOPPED_SYSTEM_REFRESHED == eventType) retType = "STOPPED_SYSTEM_REFRESHED";}.
 *
 * <h3>Regression vs WF41</h3>
 * new R8 behaviour: FrameworkEvent.STOPPED_SYSTEM_REFRESHED is an R7 constant that the R6 API of WF41 does not have;
 * with the constant replaced by its value 0x400 (the test adaptation listed in the regression-2 workstream NOTES.md)
 * ConstantsHelper.frameworkEvent fails identically on WF41 ([1024]), so the helper code is unchanged, but only the
 * R7/R8 framework fires this event type; the defect is in behaviour WF41 never had, so it is not a regression of
 * working functionality.
 */
public class FrameworkEventNameSystemRefreshedIssueTestCase {

    @Test
    public void testStoppedSystemRefreshed() {
        assertEquals("FrameworkEvent.STOPPED_SYSTEM_REFRESHED must have a name", "STOPPED_SYSTEM_REFRESHED",
                ConstantsHelper.frameworkEvent(FrameworkEvent.STOPPED_SYSTEM_REFRESHED));
    }
}
