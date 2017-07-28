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
import javax.net.ssl.SSLContext;

import org.wildfly.common.Assert;
import org.wildfly.common.function.ExceptionBiFunction;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.PeerIdentity;

/**
 * A provider for a single naming scheme.  Each implementation of a naming provider has different characteristics.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface NamingProvider extends AutoCloseable {
    /**
     * Get the location(s) of this provider.  The returned list must non-null and non-empty, but may contain
     * only one element.  The returned list should be read-only or a copy, and the underlying list should
     * not change.
     *
     * @return the locations of this provider (not {@code null} or empty)
     */
    List<Location> getLocations();

    /**
     * Get the provider URI of this provider.
     *
     * @return the provider URI of this provider (must not be {@code null})
     * @deprecated Use {@link #getLocations()} instead.
     */
    @Deprecated
    default URI getProviderUri() {
        return getLocations().get(0).getUri();
    }

    /**
     * Get a peer identity to use for context operations.  The identity may be fixed or it may vary, depending on the context configuration.
     * If the provider has multiple locations, a location is randomly selected.
     *
     * @return the peer identity to use (must not be {@code null})
     * @throws NamingException if connecting, authenticating, or re-authenticating the peer failed
     */
    default PeerIdentity getPeerIdentityForNaming() throws NamingException {
        final List<Location> locations = getLocations();
        return getPeerIdentityForNaming(locations.get(ThreadLocalRandom.current().nextInt(locations.size())));
    }

    /**
     * Get the peer identity to use for context operations for the specified location.  The identity may be fixed or it may vary, depending on the context configuration.
     * The location should be from the list returned by {@link #getLocations()}.
     *
     * @param location a location from {@link #getLocations()} (must not be {@code null})
     * @return the peer identity to use (must not be {@code null})
     * @throws NamingException if connecting, authenticating, or re-authenticating the peer failed
     */
    PeerIdentity getPeerIdentityForNaming(Location location) throws NamingException;

    /**
     * Get the authentication configuration for this naming provider, or {@code null} if no particular configuration is
     * established.
     *
     * @return the naming configuration, or {@code null} for none
     * @deprecated Use {@link #getLocations()} instead.
     */
    @Deprecated
    default AuthenticationConfiguration getAuthenticationConfiguration() {
        return getLocations().get(0).getAuthenticationConfiguration();
    }

    /**
     * Get the SSL context for this naming provider, or {@code null} if no particular SSL context is established.
     *
     * @return the SSL context, or {@code null} for none
     * @deprecated Use {@link #getLocations()} instead.
     */
    @Deprecated
    default SSLContext getSSLContext() {
        return getLocations().get(0).getSSLContext();
    }

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

    /**
     * A provider location.  A provider may be associated with one or many locations.
     */
    final class Location {
        private final URI uri;
        private final AuthenticationConfiguration authenticationConfiguration;
        private final SSLContext sslContext;

        Location(final URI uri, final AuthenticationConfiguration authenticationConfiguration, final SSLContext sslContext) {
            Assert.checkNotNullParam("uri", uri);
            this.uri = uri;
            this.authenticationConfiguration = authenticationConfiguration;
            this.sslContext = sslContext;
        }

        /**
         * Get the URI of this location.
         *
         * @return the URI of this location (not {@code null})
         */
        public URI getUri() {
            return uri;
        }

        /**
         * Get the authentication configuration for this location, if any.  The authentication configuration may
         * override the destination information in the URI.
         *
         * @return the authentication configuration, or {@code null} if none is specified
         */
        public AuthenticationConfiguration getAuthenticationConfiguration() {
            return authenticationConfiguration;
        }

        /**
         * Get the SSL context for this location, if any.
         *
         * @return the SSL context for this location, or {@code null} if none is specified
         */
        public SSLContext getSSLContext() {
            return sslContext;
        }

        public static Location of(URI uri) {
            return new Location(uri, null, null);
        }

        public static Location of(URI uri, AuthenticationConfiguration authenticationConfiguration, SSLContext sslContext) {
            return new Location(uri, authenticationConfiguration, sslContext);
        }
    }
}
