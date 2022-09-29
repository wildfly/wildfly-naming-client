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

import static org.wildfly.naming.client.ProviderEnvironment.TIME_MASK;

import java.lang.reflect.UndeclaredThrowableException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

import javax.naming.Name;
import javax.naming.NamingException;

import org.wildfly.common.function.ExceptionBiFunction;
import org.wildfly.naming.client._private.Messages;
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
        return getPeerIdentityForNamingUsingRetry(null);
    }

    /**
     * Get a peer identity to use for context operations, retrying on failure.
     * The identity may be fixed or it may vary, depending on the context
     * configuration. If the provider has multiple locations, a location is
     * randomly selected. If the retry context is null, a retry will not occur.
     *
     * @param context the retry context for storing state required when retrying
     * @return the peer identity to use (must not be {@code null})
     * @throws NamingException if connecting, authenticating, or
     *         re-authenticating the peer failed
     */
    default PeerIdentity getPeerIdentityForNamingUsingRetry(RetryContext context) throws NamingException {
        final ProviderEnvironment environment = getProviderEnvironment();
        final ConcurrentMap<URI, Long> blocklist = environment.getBlocklist();
        List<URI> locations = environment.getProviderUris();

        if (context != null && (blocklist.size() > 0 || context.transientFailCount() > 0)) {
            long time = System.currentTimeMillis();
            List<URI> updated = new ArrayList<>(locations.size());
            for (URI location : locations) {
                Long timeout = blocklist.get(location);
                if ((timeout == null || time >= (timeout & TIME_MASK)) && !context.hasTransientlyFailed(location)) {
                    updated.add(location);
                }
            }
            locations = updated;
        }

        URI location = null;
        if (locations.size() < 1) {
            throwNoMoreDestinationsException(context);
        } else if (locations.size() == 1) {
            location = locations.get(0);
        } else {
            location = locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
        }

        if (context != null) {
            context.setCurrentDestination(location);
        }

        return getPeerIdentityForNaming(location);
    }

    /**
     * Throws either an <code>ExhaustedDestinationsException</code>
     * utilizing the information passed in the specified
     * <code>RetryContext</code>, or an explicit exception mandated by the
     * context.
     *
     * @param context the current retry context of this invocation
     * @throws ExhaustedDestinationsException if no explicit exception is specified
     * @throws NamingException if explicity required
     * @throws RuntimeException if explicity required
     */
    default void throwNoMoreDestinationsException(RetryContext context) throws NamingException {
        if (context == null) {
            throw Messages.log.noMoreDestinations();
        }

        if (context.hasExplicitFailure()) {
            Throwable throwable = context.getFailures().get(0);
            try {
                throw throwable;
            } catch (NamingException | RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new UndeclaredThrowableException(t);
            }
        }

        final ProviderEnvironment env = getProviderEnvironment();
        int blocklisted = env.getBlocklist().size();
        int transientlyFailed = context.transientFailCount();
        ExhaustedDestinationsException exception = Messages.log.noMoreDestinations(blocklisted, transientlyFailed);
        for (Throwable failure : context.getFailures()) {
            exception.addSuppressed(failure);
        }

        // Remove this call
        StackTraceElement[] stackTrace = exception.getStackTrace();
        exception.setStackTrace(Arrays.copyOfRange(stackTrace, 1, stackTrace.length - 1));
        throw exception;
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
     * Perform an action under the current naming provider.
     *
     * @param function the function to apply (must not be {@code null})
     * @param <T> the first argument type
     * @param <R> the function return type
     * @return the function return value
     */
    default <T, R> R performExceptionAction(NamingOperation<T, R> function, RetryContext contextOrNull, Name name, T param) throws NamingException {
        final NamingProvider old = CurrentNamingProvider.getAndSetCurrent(this);
        try {
            return function.apply(contextOrNull, name, param);
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
