/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import javax.naming.Context;
import javax.naming.NamingException;

import org.wildfly.naming.client.util.FastHashtable;

/**
 * A context factory which maps naming providers and name schemes to actual naming contexts.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface NamingContextFactory {
    /**
     * Determine if this factory supports the given provider and name scheme.  This method is called
     * when a JNDI operation is executed on an initial context to determine which provider should handle the operation.
     * <p>
     * A context factory should evaluate the provider's type to determine if the provider is compatible with the naming
     * scheme, and should not support producing contexts for unknown providers.
     *
     * @param namingProvider the naming provider which is handling this request, or {@code null} if it is local
     * @param nameScheme the JNDI name scheme, or {@code null} if no name scheme was given
     * @return {@code true} if this factory supports the given scheme, {@code false} otherwise
     */
    boolean supportsUriScheme(NamingProvider namingProvider, String nameScheme);

    /**
     * Create the root context for this naming scheme.  The context should capture any locally relevant information,
     * such as the relevant local security or authentication context.
     *
     * @param namingProvider the naming provider which is handling this request, or {@code null} if it is local
     * @param nameScheme the scheme in the name, or {@code null} if there is no name URL scheme
     * @param env a copy of the environment which may be consumed directly by the provider (not {@code null})
     * @return the root context (must not be {@code null})
     * @throws NamingException if the root context creation failed for some reason
     */
    Context createRootContext(NamingProvider namingProvider, String nameScheme, FastHashtable<String, Object> env) throws NamingException;
}
