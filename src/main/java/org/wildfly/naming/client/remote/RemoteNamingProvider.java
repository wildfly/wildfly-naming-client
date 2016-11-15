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

package org.wildfly.naming.client.remote;

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import javax.naming.NamingException;

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client.NamingCloseable;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;

/**
 * A provider for JBoss Remoting-based JNDI contexts.  Any scheme which uses JBoss Remoting using this provider will
 * share a connection and a captured security context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteNamingProvider implements NamingProvider {
    private static final AuthenticationContextConfigurationClient CLIENT = doPrivileged((PrivilegedAction<AuthenticationContextConfigurationClient>) AuthenticationContextConfigurationClient::new);

    private final Endpoint endpoint;
    private final Supplier<IoFuture<Connection>> connectionFactory;
    private final NamingCloseable closeable;
    private final URI providerUri;
    private final AuthenticationConfiguration authenticationConfiguration;

    RemoteNamingProvider(final Endpoint endpoint, final URI providerUri, final AuthenticationContext context, final FastHashtable<String, Object> env) {
        // shared connection
        this.endpoint = endpoint;
        this.providerUri = providerUri;
        connectionFactory = () -> endpoint.getConnection(providerUri);
        closeable = NamingCloseable.NULL;
        authenticationConfiguration = CLIENT.getAuthenticationConfiguration(providerUri, context, -1, "jndi", "jboss", "operate");
    }

    RemoteNamingProvider(final Connection connection, final AuthenticationContext context, final FastHashtable<String, Object> env) {
        // separate, direct-managed connection
        this.endpoint = connection.getEndpoint();
        final URI providerUri = connection.getPeerURI();
        this.providerUri = providerUri;
        authenticationConfiguration = CLIENT.getAuthenticationConfiguration(providerUri, context, -1, "jndi", "jboss", "operate");
        connectionFactory = () -> new FinishedIoFuture<>(connection);
        closeable = () -> {
            try {
                connection.close();
            } catch (IOException e) {
                throw Messages.log.namingProviderCloseFailed(e);
            }
        };
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
     * Get the connection peer identity for a naming operation.  If the connection is not configured as {@code immediate}, then the connection
     * will not actually be established until this method is called.  The resultant connection should be closed and
     * discarded in the event of an error, in order to facilitate automatic reconnection.
     *
     * @return the connection peer identity (not {@code null})
     * @throws NamingException if connecting, authenticating, or re-authenticating the peer failed
     */
    public ConnectionPeerIdentity getPeerIdentityForNaming() throws NamingException {
        try {
            return getPeerIdentity();
        } catch (IOException e) {
            throw Messages.log.connectFailed(e);
        } catch (AuthenticationException e) {
            throw Messages.log.authenticationFailed(e);
        }
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
        return connectionFactory.get().get().getPeerIdentityContext().authenticate(authenticationConfiguration);
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
        connectionFactory.get().addNotifier(new IoFuture.HandlingNotifier<Connection, FutureResult<ConnectionPeerIdentity>>() {
            public void handleCancelled(final FutureResult<ConnectionPeerIdentity> attachment) {
                attachment.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<ConnectionPeerIdentity> attachment) {
                attachment.setException(exception);
            }

            public void handleDone(final Connection data, final FutureResult<ConnectionPeerIdentity> attachment) {
                try {
                    attachment.setResult(data.getPeerIdentityContext().authenticate(authenticationConfiguration));
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
