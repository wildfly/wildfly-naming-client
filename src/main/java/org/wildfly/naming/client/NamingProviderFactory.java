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
     * @param env a copy of the environment which may be consumed directly by the provider (not {@code null})
     * @param providerEnvironment the provider environment (not {@code null})
     * @return the root context (must not be {@code null})
     * @throws NamingException if the root context creation failed for some reason
     */
    NamingProvider createProvider(FastHashtable<String, Object> env, ProviderEnvironment providerEnvironment) throws NamingException;
}
