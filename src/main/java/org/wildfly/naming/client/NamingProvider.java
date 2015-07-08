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

import java.net.URI;

import javax.naming.Context;

import org.wildfly.naming.client.util.FastHashtable;

/**
 * A provider supported by the unified WildFly Naming dispatcher.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface NamingProvider {
    /**
     * Determine if this provider supports the given {@code PROVIDER_URL} scheme and name scheme.  This method is called
     * when a JNDI operation is executed on an initial context to determine which provider should handle the operation.
     *
     * @param providerScheme the provider URL scheme, or {@code null} if no provider URL was given
     * @param nameScheme the JNDI name scheme, or {@code null} if no name scheme was given
     * @return {@code true} if this provider supports the given schemes, {@code false} otherwise
     */
    boolean supportsUriScheme(String providerScheme, String nameScheme);

    /**
     * Create the root context for this provider.
     *
     * @param nameScheme the scheme in the name, or {@code null} if there is no name URL scheme
     * @param providerUri the URI from the {@link Context#PROVIDER_URL} environment property
     * @param env a copy of the environment which may be consumed directly by the provider
     * @return the root context
     */
    Context createRootContext(String nameScheme, URI providerUri, FastHashtable<String, Object> env);
}
