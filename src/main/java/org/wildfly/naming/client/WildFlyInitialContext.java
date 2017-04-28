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


import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.ldap.InitialLdapContext;

import org.wildfly.naming.client.util.FastHashtable;

/**
 * An initial context implementation that discovers {@link NamingProviderFactory} implementations for dispatching naming
 * requests.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class WildFlyInitialContext extends InitialLdapContext {

    private final WildFlyRootContext rootContext;

    /**
     * Construct a new instance.
     *
     * @throws NamingException if constructing the initial context fails
     */
    public WildFlyInitialContext() throws NamingException {
        this(new FastHashtable<>());
    }

    /**
     * Construct a new instance.
     *
     * @param environment the initial environment
     * @throws NamingException if constructing the initial context fails
     */
    @SuppressWarnings("unchecked")
    public WildFlyInitialContext(final Hashtable<?, ?> environment) throws NamingException {
        rootContext = new WildFlyRootContext(new FastHashtable<>((Hashtable<String, Object>) environment));
    }

    protected void init(final Hashtable<?, ?> environment) throws NamingException {
    }

    protected Context getDefaultInitCtx() throws NamingException {
        throw new NoInitialContextException();
    }
    
    public FastHashtable<String, Object> getEnvironment() throws NamingException {
        return rootContext.getEnvironment();
    }

    public Object addToEnvironment(final String propName, final Object propVal) throws NamingException {
        return rootContext.addToEnvironment(propName, propVal);
    }

    public Object removeFromEnvironment(final String propName) throws NamingException {
        return rootContext.removeFromEnvironment(propName);
    }

    public String getNameInNamespace() throws NamingException {
        return "";
    }
}
