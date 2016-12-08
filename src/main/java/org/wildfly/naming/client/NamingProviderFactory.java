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
import javax.naming.NamingException;

import org.wildfly.naming.client.util.FastHashtable;

/**
 * A provider supported by the unified WildFly Naming dispatcher.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface NamingProviderFactory {
    /**
     * Determine if this provider supports the given {@code PROVIDER_URL} scheme.
     *
     * @param providerScheme the provider URL scheme, or {@code null} if no provider URL was given
     * @param env a copy of the environment which may be used to determine if this provider supports the given scheme
     * @return {@code true} if this provider supports the given schemes, {@code false} otherwise
     */
    boolean supportsUriScheme(String providerScheme, FastHashtable<String, Object> env);

    /**
     * Create the naming provider instance for a provider URI.
     *
     * @param providerUri the URI from the {@link Context#PROVIDER_URL} environment property, or {@code null} if none was given
     * @param env a copy of the environment which may be consumed directly by the provider (not {@code null})
     * @return the root context (must not be {@code null})
     * @throws NamingException if the root context creation failed for some reason
     */
    NamingProvider createProvider(URI providerUri, FastHashtable<String, Object> env) throws NamingException;
}
