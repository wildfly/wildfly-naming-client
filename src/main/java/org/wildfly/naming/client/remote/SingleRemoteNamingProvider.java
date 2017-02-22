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

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import javax.naming.NamingException;
import javax.net.ssl.SSLContext;

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client.NamingCloseable;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.xnio.FutureResult;
import org.xnio.IoFuture;

/**
 * A provider for JBoss Remoting-based JNDI contexts.  Any scheme which uses JBoss Remoting using this provider will
 * share a connection and a captured security context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class SingleRemoteNamingProvider extends RemoteNamingProvider {

    private final Endpoint endpoint;
    private final Supplier<IoFuture<Connection>> connectionFactory;
    private final NamingCloseable closeable;
    private final URI providerUri;
    private final AuthenticationConfiguration authenticationConfiguration;

    SingleRemoteNamingProvider(final Endpoint endpoint, final URI providerUri, final AuthenticationConfiguration connectionConfiguration, final AuthenticationConfiguration operateConfiguration, final SSLContext sslContext, final FastHashtable<String, Object> env) {
        // shared connection
        this.endpoint = endpoint;
        this.providerUri = providerUri;
        connectionFactory = () -> endpoint.getConnection(providerUri, sslContext, connectionConfiguration, operateConfiguration);
        closeable = NamingCloseable.NULL;
        this.authenticationConfiguration = operateConfiguration;
    }

    /**
     * Get the Remoting endpoint for this provider.
     *
     * @return the Remoting endpoint for this provider (not {@code null})
     */
    public Endpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Get the connection peer identity.  If the connection is not configured as {@code immediate}, then the connection
     * will not actually be established until this method is called.  The resultant connection should be closed and
     * discarded in the event of an error, in order to facilitate automatic reconnection.
     *
     * @return the connection peer identity (not {@code null})
     * @throws AuthenticationException if authenticating or re-authenticating the peer failed
     * @throws IOException if connecting the peer failed
     */
    public ConnectionPeerIdentity getPeerIdentity() throws AuthenticationException, IOException {
        final Connection connection = doPrivileged((PrivilegedAction<IoFuture<Connection>>) connectionFactory::get).get();
        if (connection.supportsRemoteAuth()) {
            return connection.getPeerIdentityContext().authenticate(authenticationConfiguration);
        } else {
            return connection.getConnectionPeerIdentity();
        }
    }

    /**
     * Get the future connection peer identity.  If the connection is not configured as {@code immediate}, then the connection
     * will not actually be established until this method is called.  The resultant connection should be closed and
     * discarded in the event of an error, in order to facilitate automatic reconnection.
     *
     * @return the future connection peer identity (not {@code null})
     */
    public IoFuture<ConnectionPeerIdentity> getFuturePeerIdentity() {
        final FutureResult<ConnectionPeerIdentity> futureResult = new FutureResult<>();
        doPrivileged((PrivilegedAction<IoFuture<Connection>>) connectionFactory::get).addNotifier(new IoFuture.HandlingNotifier<Connection, FutureResult<ConnectionPeerIdentity>>() {
            public void handleCancelled(final FutureResult<ConnectionPeerIdentity> attachment) {
                attachment.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<ConnectionPeerIdentity> attachment) {
                attachment.setException(exception);
            }

            public void handleDone(final Connection data, final FutureResult<ConnectionPeerIdentity> attachment) {
                try {
                    if (data.supportsRemoteAuth()) {
                        attachment.setResult(data.getPeerIdentityContext().authenticate(authenticationConfiguration));
                    } else {
                        attachment.setResult(data.getConnectionPeerIdentity());
                    }
                } catch (AuthenticationException e) {
                    attachment.setException(new javax.security.sasl.AuthenticationException(e.getMessage(), e));
                }
            }
        }, futureResult);
        return futureResult.getIoFuture();
    }

    public URI getProviderUri() {
        return providerUri;
    }

    public void close() throws NamingException {
        closeable.close();
    }
}
