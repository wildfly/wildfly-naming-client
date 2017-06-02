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

import javax.naming.NamingException;

import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.Endpoint;

import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.security.auth.AuthenticationException;
import org.xnio.IoFuture;

/**
 * A provider for JBoss Remoting-based JNDI contexts.  Any scheme which uses JBoss Remoting using this provider will
 * share a connection and a captured security context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class RemoteNamingProvider implements NamingProvider {

    /**
     * Construct a new instance.
     */
    protected RemoteNamingProvider() {
    }

    /**
     * Get the Remoting endpoint for this provider.
     *
     * @return the Remoting endpoint for this provider (not {@code null})
     */
    public abstract Endpoint getEndpoint();

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
        } catch (AuthenticationException e) {
            throw Messages.log.authenticationFailed(e);
        } catch (IOException e) {
            throw Messages.log.connectFailed(e);
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
    public abstract ConnectionPeerIdentity getPeerIdentity() throws AuthenticationException, IOException;

    /**
     * Get the future connection peer identity.  If the connection is not configured as {@code immediate}, then the connection
     * will not actually be established until this method is called.  The resultant connection should be closed and
     * discarded in the event of an error, in order to facilitate automatic reconnection.
     *
     * @return the future connection peer identity (not {@code null})
     */
    public abstract IoFuture<ConnectionPeerIdentity> getFuturePeerIdentity();

    public abstract URI getProviderUri();

    public abstract void close() throws NamingException;

}
