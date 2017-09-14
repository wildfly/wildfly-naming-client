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

import java.net.URI;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

import javax.naming.NamingException;

import org.wildfly.common.function.ExceptionBiFunction;
import org.wildfly.security.auth.client.PeerIdentity;

/**
 * A provider for a single naming scheme.  Each implementation of a naming provider has different characteristics.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface NamingProvider extends AutoCloseable {

    /**
     * Get the provider environment.
     *
     * @return the provider environment (must not be {@code null})
     */
    ProviderEnvironment getProviderEnvironment();

    /**
     * Get a peer identity to use for context operations.  The identity may be fixed or it may vary, depending on the context configuration.
     * If the provider has multiple locations, a location is randomly selected.
     *
     * @return the peer identity to use (must not be {@code null})
     * @throws NamingException if connecting, authenticating, or re-authenticating the peer failed
     */
    default PeerIdentity getPeerIdentityForNaming() throws NamingException {
        final List<URI> locations = getProviderEnvironment().getProviderUris();
        return getPeerIdentityForNaming(locations.get(ThreadLocalRandom.current().nextInt(locations.size())));
    }

    /**
     * Get the peer identity to use for context operations for the specified location.  The identity may be fixed or it may vary, depending on the context configuration.
     * The location should be from the list returned by {@link ProviderEnvironment#getProviderUris()}.
     *
     * @param location a location from {@link ProviderEnvironment#getProviderUris()} (must not be {@code null})
     * @return the peer identity to use (must not be {@code null})
     * @throws NamingException if connecting, authenticating, or re-authenticating the peer failed
     */
    PeerIdentity getPeerIdentityForNaming(URI location) throws NamingException;

    /**
     * Get the current naming provider being used for the current deserialization operation.
     *
     * @return the current naming provider, or {@code null} if no provider-related deserialization is occurring
     */
    static NamingProvider getCurrentNamingProvider() {
        return CurrentNamingProvider.getCurrent();
    }

    /**
     * Perform an action under the current naming provider.
     *
     * @param function the function to apply (must not be {@code null})
     * @param arg1 the first argument
     * @param arg2 the second argument
     * @param <T> the first argument type
     * @param <U> the second argument type
     * @param <R> the function return type
     * @return the function return value
     */
    default <T, U, R> R performAction(BiFunction<T, U, R> function, T arg1, U arg2) {
        final NamingProvider old = CurrentNamingProvider.getAndSetCurrent(this);
        try {
            return function.apply(arg1, arg2);
        } finally {
            CurrentNamingProvider.setCurrent(old);
        }
    }

    /**
     * Perform an action under the current naming provider.
     *
     * @param function the function to apply (must not be {@code null})
     * @param arg1 the first argument
     * @param arg2 the second argument
     * @param <T> the first argument type
     * @param <U> the second argument type
     * @param <R> the function return type
     * @param <E> the function exception type
     * @return the function return value
     * @throws E if the function throws an exception of the given type
     */
    default <T, U, R, E extends Exception> R performExceptionAction(ExceptionBiFunction<T, U, R, E> function, T arg1, U arg2) throws E {
        final NamingProvider old = CurrentNamingProvider.getAndSetCurrent(this);
        try {
            return function.apply(arg1, arg2);
        } finally {
            CurrentNamingProvider.setCurrent(old);
        }
    }

    /**
     * Close the provider.  This method is called when the corresponding {@code InitialContext} is closed.  This method
     * should be idempotent.
     *
     * @throws NamingException if an error occurred while closing this provider
     */
    default void close() throws NamingException {
    }
}
