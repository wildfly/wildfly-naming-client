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
import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.CommunicationException;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingException;

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client.AbstractFederatingContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
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
        return provider.performExceptionAction((name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            return getRemoteTransport(peerIdentity).lookup(this, name_, peerIdentity, false);
        }, realName, null);
    }

    protected Object lookupLinkNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        if (realName.isEmpty()) {
            return new RemoteContext(provider, scheme, getEnvironment());
        }
        return provider.performExceptionAction((name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            return getRemoteTransport(peerIdentity).lookup(this, name_, peerIdentity, true);
        }, realName, null);
    }

    protected void bindNative(final Name name, final Object obj) throws NamingException {
        Name realName = getRealName(name);
        provider.performExceptionAction((name_, obj_) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            getRemoteTransport(peerIdentity).bind(name_, obj_, peerIdentity, false);
            return null;
        }, realName, obj);
    }

    protected void rebindNative(final Name name, final Object obj) throws NamingException {
        Name realName = getRealName(name);
        provider.performExceptionAction((name_, obj_) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            getRemoteTransport(peerIdentity).bind(name_, obj_, peerIdentity, true);
            return null;
        }, realName, obj);
    }

    protected void unbindNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        provider.performExceptionAction((name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            getRemoteTransport(peerIdentity).unbind(name_, peerIdentity);
            return null;
        }, realName, null);
    }

    protected void renameNative(final Name oldName, final Name newName) throws NamingException {
        Name realOldName = getRealName(oldName);
        Name realNewName = getRealName(newName);
        provider.performExceptionAction((oldName_, newName_) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            getRemoteTransport(peerIdentity).rename(oldName_, newName_, peerIdentity);
            return null;
        }, realOldName, realNewName);
    }

    protected CloseableNamingEnumeration<NameClassPair> listNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        return provider.performExceptionAction((name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            return getRemoteTransport(peerIdentity).list(name_, peerIdentity);
        }, realName, null);
    }

    protected CloseableNamingEnumeration<Binding> listBindingsNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        return provider.performExceptionAction((name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            return getRemoteTransport(peerIdentity).listBindings(name_, this, peerIdentity);
        }, realName, null);
    }

    protected void destroySubcontextNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        provider.performExceptionAction((name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            getRemoteTransport(peerIdentity).destroySubcontext(name_, peerIdentity);
            return null;
        }, realName, null);
    }

    protected Context createSubcontextNative(final Name name) throws NamingException {
        Name realName = getRealName(name);
        return provider.performExceptionAction((name_, ignored) -> {
            final ConnectionPeerIdentity peerIdentity = provider.getPeerIdentityForNaming();
            final CompositeName compositeName = NamingUtils.toCompositeName(name_);
            getRemoteTransport(peerIdentity).createSubcontext(compositeName, peerIdentity);
            return new RelativeFederatingContext(getEnvironment(), this, compositeName);
        }, realName, null);
    }

    public void close() {
        // no operation
    }

    public String getNameInNamespace() throws NamingException {
        return "";
    }
}
