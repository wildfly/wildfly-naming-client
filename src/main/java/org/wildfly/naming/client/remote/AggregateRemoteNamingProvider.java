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

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import javax.naming.NamingException;
import javax.net.ssl.SSLContext;

import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
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
        IOException e = null;
        do {
            // attempt to get the peer identity using the next provider
            try {
                ConnectionPeerIdentity peerIdentity = remoteNamingProviders[nextProvider].getPeerIdentity();
                currentProvider = nextProvider; // cache this successful provider
                return peerIdentity;
            } catch (IOException reason) {
                if (e == null) {
                    e = Messages.log.failedToConnectToAnyServer();
                }
                e.addSuppressed(reason);
                // we'll try another provider
            }
            nextProvider = (nextProvider + 1) % remoteNamingProviders.length;
        } while (nextProvider != startingProvider);

        // none of the providers could be used
        assert e != null; // because it's the only way to get here
        throw e;
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

    public AuthenticationConfiguration getAuthenticationConfiguration() {
        // TODO
        return null;
    }

    public SSLContext getSSLContext() {
        // TODO
        return null;
    }
}
