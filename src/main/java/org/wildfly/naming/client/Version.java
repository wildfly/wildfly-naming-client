/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.naming.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.wildfly.naming.client._private.Messages;

/**
 * The version of this JAR.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Version {

    private Version() {
    }

    private static final String VERSION;
    private static final String JAR_NAME;

    static {
        Properties versionProps = new Properties();
        String versionString = "(unknown)";
        String jarName = "(unknown)";
        try (final InputStream stream = Version.class.getResourceAsStream("Version.properties")) {
            if (stream != null) try (final InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                versionProps.load(reader);
                versionString = versionProps.getProperty("version", versionString);
                jarName = versionProps.getProperty("jarName", jarName);
            }
        } catch (IOException ignored) {
        }
        VERSION = versionString;
        JAR_NAME = jarName;
        try {
            Messages.log.greeting(versionString);
        } catch (Throwable ignored) {}
    }

    /**
     * Get the version.
     *
     * @return the version
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Get the JAR name.
     *
     * @return the JAR name
     */
    public static String getJarName() {
        return JAR_NAME;
    }
}
