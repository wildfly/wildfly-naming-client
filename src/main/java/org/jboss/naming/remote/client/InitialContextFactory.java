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
