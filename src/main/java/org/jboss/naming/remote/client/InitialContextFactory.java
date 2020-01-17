/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.naming.remote.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.naming.ConfigurationException;
import javax.naming.Context;
import javax.naming.NamingException;

import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client._private.Messages;

import static java.security.AccessController.doPrivileged;

/**
 * Compatibility class.
 *
 * @deprecated Use {@link WildFlyInitialContextFactory} instead.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Deprecated
public final class InitialContextFactory implements javax.naming.spi.InitialContextFactory {
    static {
        Messages.log.oldContextDeprecated();
    }

    @Deprecated
    public static final String ENDPOINT = "jboss.naming.client.endpoint";
    @Deprecated
    public static final String CONNECTION = "jboss.naming.client.connection";
    @Deprecated
    public static final String SETUP_EJB_CONTEXT = "jboss.naming.client.ejb.context";
    @Deprecated
    public static final String CALLBACK_HANDLER_KEY = "jboss.naming.client.security.callback.handler.class";
    @Deprecated
    public static final String PASSWORD_BASE64_KEY = "jboss.naming.client.security.password.base64";
    @Deprecated
    public static final String REALM_KEY = "jboss.naming.client.security.realm";

    private static final String CLIENT_PROPS_FILE_NAME = "jboss-naming-client.properties";

    private final WildFlyInitialContextFactory delegate = new WildFlyInitialContextFactory();

    /**
     * Delegate to {@link WildFlyInitialContextFactory#getInitialContext(Hashtable)}.
     *
     * @param environment the environment
     * @return the initial context
     * @throws NamingException if an error occurs
     */
    public Context getInitialContext(final Hashtable<?, ?> environment) throws NamingException {
        return delegate.getInitialContext(findClientProperties(environment));
    }

    private static ClassLoader getClientClassLoader() {
        final ClassLoader tccl = secureGetContextClassLoader();
        if (tccl != null) {
            return tccl;
        }
        return InitialContextFactory.class.getClassLoader();
    }

    private Properties findClientProperties(final Hashtable<?, ?> env) throws ConfigurationException {
        final Properties properties = new Properties();

        // First load the props file if it exists
        findAndPopulateClientProperties(properties);

        // Now override with naming env entries
        for (Map.Entry<?, ?> entry : env.entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                properties.setProperty((String) entry.getKey(), (String) entry.getValue());
            }
            if (entry.getKey() instanceof String &&
                    (entry.getValue() instanceof Integer || entry.getValue() instanceof Long)) {
                properties.setProperty((String) entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        return properties;
    }

    private void findAndPopulateClientProperties(final Properties props) throws ConfigurationException {
        final ClassLoader classLoader = getClientClassLoader();
        Messages.log.debug("Looking for " + CLIENT_PROPS_FILE_NAME + " using classloader " + classLoader);

        // find from classloader
        InputStream clientPropsInputStream = classLoader.getResourceAsStream(CLIENT_PROPS_FILE_NAME);

        if (clientPropsInputStream != null) {
            Messages.log.debug("Found " + CLIENT_PROPS_FILE_NAME + " using classloader " + classLoader);
            Messages.log.oldClientPropertyFileDeprecated();
            try (InputStreamReader reader = new InputStreamReader(clientPropsInputStream, StandardCharsets.UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                final Properties clientProps = new Properties();
                clientProps.load(bufferedReader);
                // now populate the props with loaded client properties
                props.putAll(clientProps);
            } catch (IOException e) {
                final ConfigurationException ce = new ConfigurationException("Could not load " + CLIENT_PROPS_FILE_NAME);
                ce.initCause(e);
                throw ce;
            }
        }
    }

    private static ClassLoader secureGetContextClassLoader() {
        final ClassLoader contextClassLoader;
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            contextClassLoader = doPrivileged((PrivilegedAction<ClassLoader>) InitialContextFactory::getContextClassLoader);
        } else {
            contextClassLoader = getContextClassLoader();
        }
        return contextClassLoader == null ? InitialContextFactory.class.getClassLoader() : contextClassLoader;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

}
