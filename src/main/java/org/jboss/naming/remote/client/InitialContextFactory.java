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

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;

import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client._private.Messages;

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

    private final WildFlyInitialContextFactory delegate = new WildFlyInitialContextFactory();

    /**
     * Delegate to {@link WildFlyInitialContextFactory#getInitialContext(Hashtable)}.
     *
     * @param environment the environment
     * @return the initial context
     * @throws NamingException if an error occurs
     */
    public Context getInitialContext(final Hashtable<?, ?> environment) throws NamingException {
        return delegate.getInitialContext(environment);
    }
}
