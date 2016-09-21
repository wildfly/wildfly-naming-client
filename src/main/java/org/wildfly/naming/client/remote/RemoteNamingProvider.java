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

import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;

import javax.naming.NamingException;

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client.NamingCloseable;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.FinishedIoFuture;
import org.xnio.IoFuture;

/**
 * A provider for JBoss Remoting-based JNDI contexts.  Any scheme which uses JBoss Remoting using this provider will
 * share a connection and a captured security context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteNamingProvider implements NamingProvider {
    private final Endpoint endpoint;
    private final AuthenticationContext capturedAuthenticationContext;
    private final Supplier<IoFuture<Connection>> connectionFactory;
    private final NamingCloseable closeable;
    private final URI providerUri;

    RemoteNamingProvider(final Endpoint endpoint, final URI providerUri, final AuthenticationContext context, final FastHashtable<String, Object> env) {
        this.endpoint = endpoint;
        this.providerUri = providerUri;
        capturedAuthenticationContext = context;
        connectionFactory = () -> endpoint.getConnection(providerUri);
        closeable = NamingCloseable.NULL;
    }

    RemoteNamingProvider(final Connection connection, final AuthenticationContext context, final FastHashtable<String, Object> env) {
        this.endpoint = connection.getEndpoint();
        providerUri = connection.getPeerURI();
        capturedAuthenticationContext = context;
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
     * Get the connection.  If the connection is not configured as {@code immediate}, then the connection
     * will not actually be established until this method is called.  The resultant connection should be closed and
     * discarded in the event of an error, in order to facilitate automatic reconnection.
     *
     * @return the connection (not {@code null})
     * @throws IOException if the connection was not established and establishment failed
     */
    public Connection getConnection() throws IOException {
        return connectionFactory.get().get();
    }

    /**
     * Get the captured authentication context.
     *
     * @return the captured authentication context (not {@code null})
     */
    public AuthenticationContext getCapturedAuthenticationContext() {
        return capturedAuthenticationContext;
    }

    public URI getProviderUri() {
        return providerUri;
    }

    public void close() throws NamingException {
        closeable.close();
    }
}
