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
