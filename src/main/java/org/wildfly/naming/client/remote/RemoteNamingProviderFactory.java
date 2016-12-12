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
import static org.jboss.naming.remote.client.InitialContextFactory.CALLBACK_HANDLER_KEY;
import static org.jboss.naming.remote.client.InitialContextFactory.CONNECTION;
import static org.jboss.naming.remote.client.InitialContextFactory.ENDPOINT;
import static org.jboss.naming.remote.client.InitialContextFactory.PASSWORD_BASE64_KEY;
import static org.jboss.naming.remote.client.InitialContextFactory.REALM_KEY;

import java.io.IOException;
import java.net.URI;
import java.security.PrivilegedAction;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.kohsuke.MetaInfServices;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.NamingProviderFactory;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.util.CodePointIterator;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class RemoteNamingProviderFactory implements NamingProviderFactory {
    public RemoteNamingProviderFactory() {
    }

    /**
     * An environment attribute indicating that a separate Remoting connection should be used for this initial context.  Normally,
     * it is preferable to use managed connections, but in some special circumstances it is expedient to create an
     * unmanaged, separate connection.  Note that using unmanaged connections can result in additional resource consumption
     * and should be avoided in most situations.
     * <p>
     * Separate connections use the captured authentication context for authentication decisions unless a principal or
     * authentication configuration is set in the environment.
     * <p>
     * A separate connection's lifespan is bound to the lifespan of the {@code InitialContext} that it belongs to.  It
     * <em>must</em> be closed to prevent possible resource exhaustion.
     */
    public static final String USE_SEPARATE_CONNECTION = "org.wildfly.naming.client.remote.use-separate-connection";

    static final Attachments.Key<RemoteNamingProvider> PROVIDER_KEY = new Attachments.Key<>(RemoteNamingProvider.class);

    private static final Attachments.Key<ProviderMap> PROVIDER_MAP_KEY = new Attachments.Key<>(ProviderMap.class);

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    public boolean supportsUriScheme(final String providerScheme, final FastHashtable<String, Object> env) {
        final Endpoint endpoint = getEndpoint(env);
        return endpoint != null && endpoint.isValidUriScheme(providerScheme);
    }

    public NamingProvider createProvider(final URI providerUri, final FastHashtable<String, Object> env) throws NamingException {
        // Legacy naming constants
        final Endpoint endpoint = getEndpoint(env);
        final String callbackClass = getStringProperty(CALLBACK_HANDLER_KEY, env);
        final String userName = getStringProperty(Context.SECURITY_PRINCIPAL, env);
        final String password = getStringProperty(Context.SECURITY_CREDENTIALS, env);
        final String passwordBase64 = getStringProperty(PASSWORD_BASE64_KEY, env);
        final String realm = getStringProperty(REALM_KEY, env);

        boolean useSeparateConnection = Boolean.parseBoolean(String.valueOf(env.get(USE_SEPARATE_CONNECTION)));

        AuthenticationContext captured = AuthenticationContext.captureCurrent();
        AuthenticationConfiguration mergedConfiguration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(providerUri, captured);
        if (callbackClass != null && (userName != null || password != null)) {
            throw Messages.log.callbackHandlerAndUsernameAndPasswordSpecified();
        }
        if (callbackClass != null) {
            final ClassLoader classLoader = secureGetContextClassLoader();
            try {
                final Class<?> clazz = Class.forName(callbackClass, true, classLoader);
                final CallbackHandler callbackHandler = (CallbackHandler) clazz.newInstance();
                if (callbackHandler != null) {
                    mergedConfiguration = mergedConfiguration.useCallbackHandler(callbackHandler);
                }
            } catch (ClassNotFoundException e) {
                throw Messages.log.failedToLoadCallbackHandlerClass(e, callbackClass);
            } catch (Exception e) {
                throw Messages.log.failedToInstantiateCallbackHandlerInstance(e, callbackClass);
            }
        } else if (userName != null) {
            if (password != null && passwordBase64 != null) {
                throw Messages.log.plainTextAndBase64PasswordSpecified();
            }
            final String decodedPassword = passwordBase64 != null ? CodePointIterator.ofString(passwordBase64).base64Decode().asUtf8String().drainToString() : password;
            mergedConfiguration = mergedConfiguration.useName(userName).usePassword(decodedPassword).useRealm(realm);
        }
        final AuthenticationContext context = AuthenticationContext.empty().with(MatchRule.ALL, mergedConfiguration);

        if (useSeparateConnection) {
            // create a brand new connection - if there is authentication info in the env, use it
            final Connection connection;
            try {
                connection = endpoint.connect(providerUri, OptionMap.EMPTY, context).get();
            } catch (IOException e) {
                throw Messages.log.connectFailed(e);
            }
            final RemoteNamingProvider provider = new RemoteNamingProvider(connection, context, env);
            connection.getAttachments().attach(PROVIDER_KEY, provider);
            return provider;
        } else if (env.containsKey(CONNECTION)) {
            final Connection connection = (Connection) env.get(CONNECTION);
            final RemoteNamingProvider provider = new RemoteNamingProvider(connection, context, env);
            connection.getAttachments().attach(PROVIDER_KEY, provider);
            return provider;
        } else {
            final Attachments attachments = endpoint.getAttachments();
            ProviderMap map = attachments.getAttachment(PROVIDER_MAP_KEY);
            if (map == null) {
                ProviderMap appearing = attachments.attachIfAbsent(PROVIDER_MAP_KEY, map = new ProviderMap());
                if (appearing != null) {
                    map = appearing;
                }
            }
            final URIKey key = new URIKey(providerUri.getScheme(), providerUri.getUserInfo(), providerUri.getHost(), providerUri.getPort());
            RemoteNamingProvider provider = map.get(key);
            if (provider == null) {
                RemoteNamingProvider appearing = map.putIfAbsent(key, provider = new RemoteNamingProvider(endpoint, providerUri, context, env));
                if (appearing != null) {
                    provider = appearing;
                }
            }
            return provider;
        }
    }

    private Endpoint getEndpoint(final FastHashtable<String, Object> env) {
        return env.containsKey(ENDPOINT) ? (Endpoint) env.get(ENDPOINT) : Endpoint.getCurrent();
    }

    private String getStringProperty(final String propertyName, final FastHashtable<String, Object> env) {
        final Object propertyValue = env.get(propertyName);
        return propertyValue == null ? null : (String) propertyValue;
    }

    private static ClassLoader secureGetContextClassLoader() {
        final ClassLoader contextClassLoader;
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            contextClassLoader = doPrivileged((PrivilegedAction<ClassLoader>) RemoteNamingProviderFactory::getContextClassLoader);
        } else {
            contextClassLoader = getContextClassLoader();
        }
        return contextClassLoader;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    static final class URIKey {
        private final String scheme;
        private final String userInfo;
        private final String host;
        private final int port;
        private final int hashCode;

        URIKey(final String scheme, final String userInfo, final String host, final int port) {
            this.scheme = scheme == null ? "" : scheme;
            this.userInfo = userInfo == null ? "" : userInfo;
            this.host = host == null ? "" : host;
            this.port = port;
            hashCode = port + 31 * (this.host.hashCode() + 31 * (this.userInfo.hashCode() + 31 * this.scheme.hashCode()));
        }

        public boolean equals(final Object o) {
            return this == o || o instanceof URIKey && equals((URIKey) o);
        }

        private boolean equals(final URIKey key) {
            return hashCode == key.hashCode && port == key.port && scheme.equals(key.scheme) && userInfo.equals(key.userInfo) && host.equals(key.host);
        }

        public int hashCode() {
            return hashCode;
        }
    }

    @SuppressWarnings("serial")
    static final class ProviderMap extends ConcurrentHashMap<URIKey, RemoteNamingProvider> {
    }
}
