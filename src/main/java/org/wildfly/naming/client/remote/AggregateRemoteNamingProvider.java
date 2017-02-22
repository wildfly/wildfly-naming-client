/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

package org.wildfly.naming.client.remote;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import javax.naming.NamingException;

import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.security.auth.AuthenticationException;
import org.xnio.IoFuture;

/**
 * A {@link RemoteNamingProvider} which aggregates other {@code RemoteNamingProvider} instances.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
final class AggregateRemoteNamingProvider extends RemoteNamingProvider {

    private final RemoteNamingProvider[] remoteNamingProviders;
    private volatile int currentProvider;

    AggregateRemoteNamingProvider(RemoteNamingProvider... remoteNamingProviders) {
        checkNotNullParam("remoteNamingProviders", remoteNamingProviders);
        this.remoteNamingProviders = remoteNamingProviders.clone();
    }

    AggregateRemoteNamingProvider(Collection<RemoteNamingProvider> remoteNamingProviders) {
        checkNotNullParam("remoteNamingProviders", remoteNamingProviders);
        this.remoteNamingProviders = remoteNamingProviders.toArray(new RemoteNamingProvider[remoteNamingProviders.size()]);
    }

    public Endpoint getEndpoint() {
        return getCurrentProvider().getEndpoint();
    }

    public synchronized ConnectionPeerIdentity getPeerIdentity() throws AuthenticationException, IOException {
        int startingProvider = currentProvider;
        int nextProvider = startingProvider;
        do {
            // attempt to get the peer identity using the next provider
            try {
                ConnectionPeerIdentity peerIdentity = remoteNamingProviders[nextProvider].getPeerIdentity();
                currentProvider = nextProvider; // cache this successful provider
                return peerIdentity;
            } catch (IOException ignored) {
                // we'll try another provider
            }
            nextProvider = (nextProvider + 1) % remoteNamingProviders.length;
        } while (nextProvider != startingProvider);

        // none of the providers could be used
        throw Messages.log.failedToConnectToAnyServer();
    }

    public IoFuture<ConnectionPeerIdentity> getFuturePeerIdentity() {
        return getCurrentProvider().getFuturePeerIdentity();
    }

    public URI getProviderUri() {
        return getCurrentProvider().getProviderUri();
    }

    public void close() throws NamingException {
        boolean exceptionOnClose = false;
        for (RemoteNamingProvider remoteNamingProvider : remoteNamingProviders) {
            try {
                remoteNamingProvider.close();
            } catch (NamingException e) {
                exceptionOnClose = true;
            }
        }
        if (exceptionOnClose) {
            throw Messages.log.failedToCloseNamingProviders();
        }
    }

    private RemoteNamingProvider getCurrentProvider() {
        return remoteNamingProviders[currentProvider];
    }
}
