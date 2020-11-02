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

package org.wildfly.naming.client.remote;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.IdentityHashMap;

import javax.naming.Binding;
import javax.naming.CommunicationException;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client.AbstractFederatingContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.ExhaustedDestinationsException;
import org.wildfly.naming.client.NamingOperation;
import org.wildfly.naming.client.ProviderEnvironment;
import org.wildfly.naming.client.RetryContext;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.store.RelativeFederatingContext;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.naming.client.util.NamingUtils;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * The remote-server root context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteContext extends AbstractFederatingContext {

    private static final int MAX_NOT_FOUND_RETRY = 8;
    private final RemoteNamingProvider provider;
    private final String scheme;

    RemoteContext(final RemoteNamingProvider provider, final String scheme, final Hashtable<String, Object> env) throws CommunicationException {
        super(FastHashtable.of(env));
        this.provider = provider;
        this.scheme = scheme;
    }

    RemoteClientTransport getRemoteTransport(ConnectionPeerIdentity peerIdentity) throws NamingException {
        final Endpoint endpoint = provider.getEndpoint();
        if (endpoint == null) {
            throw Messages.log.noRemotingEndpoint();
        }
        try {
            final Connection connection = peerIdentity.getConnection();
            final IoFuture<RemoteClientTransport> future = RemoteClientTransport.SERVICE_HANDLE.getClientService(connection, OptionMap.EMPTY);
            try {
                return future.getInterruptibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel();
                throw Messages.log.operationInterrupted();
            }
        } catch (IOException e) {
            if (e.getCause() instanceof NamingException) {
                throw (NamingException) e.getCause();
            }
            throw Messages.log.connectFailed(e);
        }
    }

    private boolean canRetry(ProviderEnvironment environment) {
        return environment.getProviderUris().size() > 1;
    }

    private  <T, R> R performWithRetry(NamingOperation<T, R> function, ProviderEnvironment environment, RetryContext context, Name name, T param) throws NamingException {
        // Directly pass-through single provider executions
        if (context == null) {
            return provider.performExceptionAction(function, context, name, param);
        }

        for (int notFound = 0;;) {
            try {
                R result = provider.performExceptionAction(function, context, name, param);
                environment.dropFromBlocklist(context.currentDestination());
                return result;
            } catch (NameNotFoundException e) {
                if (notFound++ > MAX_NOT_FOUND_RETRY) {
                    Messages.log.tracef("Maximum name not found attempts exceeded,");
                    throw e;
                }
                URI location = context.currentDestination();
                Messages.log.tracef("Provider (%s) did not have name \"%s\" (or a portion), retrying other nodes", location, name);

                // Always throw NameNotFoundException, unless we find it on another host
                context.addExplicitFailure(e);
                context.addTransientFail(location);
            } catch (ExhaustedDestinationsException e) {
                throw e;
            } catch (CommunicationException t) {
                URI location = context.currentDestination();
                Messages.log.tracef(t, "Communication error while contacting %s", location);
                updateBlocklist(environment, context, t);
                context.addFailure(injectDestination(t, location));
            } catch (NamingException e) {
                // All other naming exceptions are legit errors
                environment.dropFromBlocklist(context.currentDestination());
                throw e;
            } catch (Throwable t) {
                // Don't blocklist generic throwables since it may indicate a client bug
                URI location = context.currentDestination();
                Messages.log.tracef(t, "Unexpected throwable while contacting %s", location);
                context.addTransientFail(location);
                context.addFailure(injectDestination(t, location));
            }
        }
    }

    private static Throwable injectDestination(Throwable t, URI destination) {
        StackTraceElement[] stackTrace = new StackTraceElement[5];
        System.arraycopy(t.getStackTrace(), 0, stackTrace, 1, 4);
        stackTrace[0] = new StackTraceElement("", "..use of destination...", destination.toString(), -1);
        t.setStackTrace(stackTrace);

        IdentityHashMap<Throwable, Throwable> encountered = new IdentityHashMap<>(3);
        encountered.put(t, t);
        Throwable cause = t.getCause();
        while (cause != null && encountered.get(cause) == null) {
            encountered.put(cause, cause);
            cause.setStackTrace(Arrays.copyOfRange(cause.getStackTrace(), 0, 5));
            cause = cause.getCause();
        }

        return t;
    }

    private void updateBlocklist(ProviderEnvironment environment, RetryContext context, Throwable t) {
        URI location = context.currentDestination();
        Messages.log.tracef(t, "Provider (%s) failed, blocklisting and retrying", location);
        environment.updateBlocklist(location);
    }

    Name getRealName(Name name) throws InvalidNameException {
        // this could go away after WFNC-20
        if (scheme == null) {
            return name;
        }
        if (name.isEmpty()) {
            return new CompositeName(scheme + ":");
        }
        final String part0 = name.get(0);
        final Name clone = (Name) name.clone();
        clone.remove(0);
        clone.add(0, scheme + ":" + part0);
        return clone;
    }

    protected Object lookupNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        if (realName.isEmpty()) {
            return new RemoteContext(provider, scheme, getEnvironment());
        }

        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        return performWithRetry((context_, name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            return getRemoteTransport(peerIdentity).lookup(this, name_, peerIdentity, false);
        }, environment, context, realName, null);
    }

    protected Object lookupLinkNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        if (realName.isEmpty()) {
            return new RemoteContext(provider, scheme, getEnvironment());
        }
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        return performWithRetry((context_, name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            return getRemoteTransport(peerIdentity).lookup(this, name_, peerIdentity, true);
        }, environment, context, realName, null);
    }

    protected void bindNative(final Name name, final Object obj) throws NamingException {
        Name realName = getRealName(name);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        performWithRetry((context_, name_, obj_) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            getRemoteTransport(peerIdentity).bind(name_, obj_, peerIdentity, false);
            return null;
        }, environment, context, realName, obj);
    }

    protected void rebindNative(final Name name, final Object obj) throws NamingException {
        Name realName = getRealName(name);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        performWithRetry((context_, name_, obj_) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            getRemoteTransport(peerIdentity).bind(name_, obj_, peerIdentity, true);
            return null;
        }, environment, context, realName, obj);
    }

    protected void unbindNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        performWithRetry((context_, name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            getRemoteTransport(peerIdentity).unbind(name_, peerIdentity);
            return null;
        }, environment, context, realName, null);
    }

    protected void renameNative(final Name oldName, final Name newName) throws NamingException {
        Name realOldName = getRealName(oldName);
        Name realNewName = getRealName(newName);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        performWithRetry((context_, oldName_, newName_) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            getRemoteTransport(peerIdentity).rename(oldName_, newName_, peerIdentity);
            return null;
        }, environment, context, realOldName, realNewName);
    }

    protected CloseableNamingEnumeration<NameClassPair> listNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        return performWithRetry((context_, name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            return getRemoteTransport(peerIdentity).list(name_, peerIdentity);
        }, environment, context, realName, null);
    }

    protected CloseableNamingEnumeration<Binding> listBindingsNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        return performWithRetry((context_, name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            return getRemoteTransport(peerIdentity).listBindings(name_, this, peerIdentity);
        }, environment, context, realName, null);
    }

    protected void destroySubcontextNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        performWithRetry((context_, name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            getRemoteTransport(peerIdentity).destroySubcontext(name_, peerIdentity);
            return null;
        }, environment, context, realName, null);
    }

    protected Context createSubcontextNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        ProviderEnvironment environment = provider.getProviderEnvironment();
        final RetryContext context = canRetry(environment) ? new RetryContext() : null;

        return performWithRetry((context_, name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNamingUsingRetry(context_);
            final CompositeName compositeName = NamingUtils.toCompositeName(name_);
            getRemoteTransport(peerIdentity).createSubcontext(compositeName, peerIdentity);
            return new RelativeFederatingContext(getEnvironment(), this, compositeName);
        }, environment, context, realName, null);
    }

    public void close() {
        // no operation
    }

    public String getNameInNamespace() throws NamingException {
        return "";
    }
}
