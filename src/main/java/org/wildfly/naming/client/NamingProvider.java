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

import java.net.URI;
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
     * Get the provider URI of this provider.
     *
     * @return the provider URI of this provider (must not be {@code null})
     */
    URI getProviderUri();

    /**
     * Get the peer identity to use for context operations.  The identity may be fixed or it may vary, depending on the context configuration.
     *
     * @return the peer identity to use (must not be {@code null})
     * @throws NamingException if connecting, authenticating, or re-authenticating the peer failed
     */
    PeerIdentity getPeerIdentityForNaming() throws NamingException;

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
