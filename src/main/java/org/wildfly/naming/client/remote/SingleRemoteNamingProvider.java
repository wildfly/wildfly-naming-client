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
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.util.function.Supplier;

import javax.naming.NamingException;
import javax.net.ssl.SSLContext;

import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;
import org.wildfly.naming.client.NamingCloseable;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.FailedIoFuture;
import org.xnio.IoFuture;

/**
 * A provider for JBoss Remoting-based JNDI contexts.  Any scheme which uses JBoss Remoting using this provider will
 * share a connection and a captured security context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class SingleRemoteNamingProvider extends RemoteNamingProvider {

    private static final AuthenticationContextConfigurationClient CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);
    private final Endpoint endpoint;
    private final Supplier<IoFuture<ConnectionPeerIdentity>> connectionFactory;
    private final NamingCloseable closeable;
    private final URI providerUri;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final SSLContext sslContext;

    SingleRemoteNamingProvider(final Endpoint endpoint, final URI providerUri, final AuthenticationConfiguration authenticationConfiguration, final SSLContext sslContext, final FastHashtable<String, Object> env) {
        // shared connection
        this.endpoint = endpoint;
        this.providerUri = providerUri;
        this.authenticationConfiguration = authenticationConfiguration;
        this.sslContext = sslContext;
        connectionFactory = () -> {
            SSLContext realSSLContext;
            if (sslContext == null) {
                try {
                    realSSLContext = CLIENT.getSSLContext(providerUri, AuthenticationContext.captureCurrent(), "jndi", "jboss");
                } catch (GeneralSecurityException e) {
                    return new FailedIoFuture<>(new IOException(e));
                }
            } else {
                realSSLContext = sslContext;
            }
            AuthenticationConfiguration realConf;
            if (authenticationConfiguration == null) {
                realConf = CLIENT.getAuthenticationConfiguration(providerUri, AuthenticationContext.captureCurrent(), -1, "jndi", "jboss");
            } else {
                realConf = authenticationConfiguration;
            }
            return endpoint.getConnectedIdentity(providerUri, realSSLContext, realConf);
        };
        closeable = NamingCloseable.NULL;
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
        return getFuturePeerIdentity().get();
    }

    /**
     * Get the future connection peer identity.  If the connection is not configured as {@code immediate}, then the connection
     * will not actually be established until this method is called.  The resultant connection should be closed and
     * discarded in the event of an error, in order to facilitate automatic reconnection.
     *
     * @return the future connection peer identity (not {@code null})
     */
    public IoFuture<ConnectionPeerIdentity> getFuturePeerIdentity() {
        return doPrivileged((PrivilegedAction<IoFuture<ConnectionPeerIdentity>>) connectionFactory::get);
    }

    public AuthenticationConfiguration getAuthenticationConfiguration() {
        return authenticationConfiguration;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public URI getProviderUri() {
        return providerUri;
    }

    public void close() throws NamingException {
        closeable.close();
    }
}
