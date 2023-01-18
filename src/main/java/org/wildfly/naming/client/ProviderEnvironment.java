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

package org.wildfly.naming.client;

import static java.security.AccessController.doPrivileged;
import static org.wildfly.naming.client.util.EnvironmentUtils.CONNECT_OPTIONS;
import static org.wildfly.naming.client.util.EnvironmentUtils.EJB_CALLBACK_HANDLER_CLASS_KEY;
import static org.wildfly.naming.client.util.EnvironmentUtils.EJB_HOST_KEY;
import static org.wildfly.naming.client.util.EnvironmentUtils.EJB_PASSWORD_BASE64_KEY;
import static org.wildfly.naming.client.util.EnvironmentUtils.EJB_PASSWORD_KEY;
import static org.wildfly.naming.client.util.EnvironmentUtils.EJB_PORT_KEY;
import static org.wildfly.naming.client.util.EnvironmentUtils.EJB_REMOTE_CONNECTION_PROVIDER_PREFIX;
import static org.wildfly.naming.client.util.EnvironmentUtils.EJB_USERNAME_KEY;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import javax.naming.ConfigurationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.remoting3.RemotingOptions;
import org.wildfly.common.Assert;
import org.wildfly.common.expression.Expression;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.util.EnvironmentUtils;
import org.wildfly.naming.client.util.NetworkUtils;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.sasl.localuser.LocalUserClient;
import org.wildfly.common.iteration.CodePointIterator;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;

/**
 * Environmental information pertaining to a naming provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ProviderEnvironment {
    private static final long BACKOFF_MASK = 0x7FFF;
    public static final long TIME_MASK = ~BACKOFF_MASK;

    private final List<URI> providerUris;
    private final ConcurrentHashMap<URI, Long> blocklist = new ConcurrentHashMap<>(0);
    private final Supplier<AuthenticationContext> authenticationContextSupplier;

    @SuppressWarnings({ "Convert2Lambda", "Anonymous2MethodRef" })
    static final Supplier<AuthenticationContext> DEFAULT_AUTH_CTXT_SUPPLIER = new Supplier<AuthenticationContext>() {
        public AuthenticationContext get() {
            return AuthenticationContext.captureCurrent();
        }
    };

    ProviderEnvironment(Builder builder) {
        final List<URI> providerUris = builder.getProviderUris();
        this.providerUris = providerUris.isEmpty() ? Collections.emptyList() : providerUris.size() == 1 ? Collections.singletonList(providerUris.get(0)) : Collections.unmodifiableList(new ArrayList<>(providerUris));
        this.authenticationContextSupplier = builder.getAuthenticationContextSupplier();
    }

    /**
     * Get the list of provider URLs.
     *
     * @return the list of provider URLs (not {@code null})
     */
    public List<URI> getProviderUris() {
        return providerUris;
    }

    /**
     * Gets the blocklist for this provider. The map should generally not be
     * mutated, with updates instead going through {@link #updateBlocklist(URI)}
     * and {@link #dropFromBlocklist(URI)}.
     *
     * <p>The map is keyed by destination URI, as specified by
     * <code>PROVIDER_URL</code>. The value is a long that loosely corresponds
     * to an expiration timestamp. More specifically the time portion is the
     * first 49 bits, retrievable using a bitwise AND on {@link #TIME_MASK}. The
     * remaining bits are used to store the current back-off multiplier. These
     * are typically not of interest to a user or provider implementor, as
     * {@link #updateBlocklist(URI)} will update them accordingly.
     * </p>
     *
     * <p>A simple provider implementation would perform the falling
     * pseudo-code when selecting a destination: </p>
     *
     * <pre>{@code
     *
     * URI location = ...
     * Long timeout = env.getBlocklist().get(location);
     * if (timeout == null || time >= (timeout & TIME_MASK)) {
     *     // blocklist entry expired!!
     * } else {
     *     // Ignoring, still blocklisted!
     * }
     *
     * }</pre>
     *
     * @return a concurrent map representing the blocklist
     */
    public ConcurrentMap<URI, Long> getBlocklist() {
        return blocklist;
    }

    /**
     * Adds location to blocklist, or extends it's back-off value if already
     * present in the blocklist. Each call doubles the back-off time, as well
     * as resets the starting time. Providers should call this method anytime
     * a location is non-responsive.
     *
     * @param location the URI to blocklist.
     */
    public void updateBlocklist(URI location) {
        // blocklist entry value format =
        // 49 bits truncated ceiling timestamp | 15 bits backoff multiplier
        // Loss of 15 bits corresponds to roughly a minute (65535 millis)
        // multiplier doubles each update, adding a rough delay of 2^16 to 2^29
        // millis (around 1 minute to 6 days). LSB is used to pad for ceiling.

        for (;;) {
            long time = System.currentTimeMillis();

            Long entry = blocklist.get(location);
            if (entry == null) {
                // Ceiling + roughly 1.5 minutes (starting point)
                long next = (((time >>> 15) + 3L) << 15) | 2L;
                entry = blocklist.putIfAbsent(location, next);
                if (entry == null) {
                    return;
                }
            }
            int backoff = (int) ((entry & BACKOFF_MASK) << 1);
            backoff = backoff > BACKOFF_MASK ? backoff >> 1 : backoff;
            long next = (((time >>> 15) + backoff + 1) << 15) | backoff;
            if (blocklist.replace(location, entry, next)) {
                return;
            }
        }
    }

    /**
     * Removes the specified location from the blocklist, allowing it to be
     * used again.
     *
     * @param location the location to remove
     */
    public void dropFromBlocklist(URI location) {
        blocklist.remove(location);
    }

    /**
     * Get the authentication context supplier to use.  The default supplier simply captures the current context from
     * the calling thread, but this behavior can be modified by establishing authentication information on the
     * environment of the initial context or by individual naming providers.
     *
     * @return the authentication context supplier to use (must not be {@code null})
     */
    public Supplier<AuthenticationContext> getAuthenticationContextSupplier() {
        return authenticationContextSupplier;
    }

    /**
     * The builder for {@code ProviderEnvironment} instances.
     */
    public static final class Builder {
        private final List<URI> providerUris = new ArrayList<>();
        private final Set<URI> encounteredUris = new HashSet<>();
        private Supplier<AuthenticationContext> authenticationContextSupplier = DEFAULT_AUTH_CTXT_SUPPLIER;

        /**
         * Construct a new instance.
         */
        public Builder() {
        }

        /**
         * Add a provider URL to the environment being built.
         *
         * @param uri the URI of the provider (must not be {@code null})
         * @return this builder
         */
        public Builder addProviderUri(URI uri) {
            Assert.checkNotNullParam("uri", uri);
            if (! encounteredUris.add(uri)) {
                Messages.log.ignoringDuplicateDestination(uri);
            }
            providerUris.add(uri);
            return this;
        }

        /**
         * Add a provider URL to the environment being built.
         *
         * @param uris the URIs of the provider(s) (must not be {@code null} or have {@code null} members)
         * @return this builder
         */
        public Builder addProviderUris(Collection<URI> uris) {
            Assert.checkNotNullParam("uris", uris);
            for (URI uri : uris) {
                Assert.checkNotNullParam("uri", uri);
                providerUris.add(uri);
            }
            return this;
        }

        /**
         * Set the authentication context supplier to use.
         *
         * @param authenticationContextSupplier the authentication context supplier to use (must not be {@code null})
         * @return this builder
         */
        public Builder setAuthenticationContextSupplier(final Supplier<AuthenticationContext> authenticationContextSupplier) {
            Assert.checkNotNullParam("authenticationContextSupplier", authenticationContextSupplier);
            this.authenticationContextSupplier = authenticationContextSupplier;
            return this;
        }

        List<URI> getProviderUris() {
            return providerUris;
        }

        Supplier<AuthenticationContext> getAuthenticationContextSupplier() {
            return authenticationContextSupplier;
        }

        /**
         * Populate this builder from the given JNDI environment map.  The following information will be populated:
         *
         * <ul>
         *     <li>Provider URLs by reading standard and compatibility properties</li>
         *     <li>
         *         Authentication information including:
         *         <ul>
         *             <li>Authentication name</li>
         *             <li>Credential(s)</li>
         *         </ul>
         *     </li>
         * </ul>
         *
         * @param environment the environment map (must not be {@code null})
         * @return this builder (populated from the map)
         * @throws ConfigurationException if the given environment is invalid for some reason
         * @throws NamingException if some other error occurred
         */
        public Builder populateFromEnvironment(Map<String, ?> environment) throws NamingException {
            Assert.checkNotNullParam("environment", environment);

            final ClassLoader classLoader = secureGetContextClassLoader();

            // Start with the simple ones
            final String userName = getEnvString(environment, Context.SECURITY_PRINCIPAL, null, true);

            // Get provider URLs
            boolean gotProviders = populateProviderUris(environment);

            // Check for top-level authentication defaults
            final String securityProtocol = getEnvString(environment, Context.SECURITY_PROTOCOL, null, true);
            final String globalSslEnabledOption = getEnvString(environment, EJB_REMOTE_CONNECTION_PROVIDER_PREFIX + Options.SSL_ENABLED, null, true);
            final boolean isSsl = globalSslEnabledOption != null ? Boolean.parseBoolean(globalSslEnabledOption) : securityProtocol != null && "ssl".equalsIgnoreCase(securityProtocol.trim());
            final IdentityCredentials credentials = EnvironmentUtils.getSecurityCredentials(environment);


            // we definitely must override default auth if any of these are given; we _may_ have to do so if compat props are given
            boolean overrideDefaultAuth = credentials != null || userName != null;

            OptionMap remotingOptions = getOptionMap(environment, CONNECT_OPTIONS_PREFIX, Builder.class.getClassLoader());

            if (userName != null) {
                // disable quiet local auth
                remotingOptions = setQuietLocalAuth(remotingOptions, false);
            }

            AuthenticationConfiguration globalAuthConf = RemotingOptions.mergeOptionsIntoAuthenticationConfiguration(remotingOptions, AuthenticationConfiguration.empty());

            if (userName != null) {
                globalAuthConf = globalAuthConf.useName(userName);
            }
            if (credentials != null) {
                globalAuthConf = globalAuthConf.useCredentials(credentials);
            }

            // ===
            // at this point on, we are only processing legacy configuration; this may be dropped in some future version.
            // ===

            // now, spin through the environment to scan for legacy properties; they are defined on a per-connection basis
            Set<String> connections = null;
            for (String key : environment.keySet()) {
                String name = connectionNameOf(key);
                if (name != null) {
                    // this is it...
                    if (gotProviders) {
                        Messages.log.ignoringLegacyProperties();
                        // don't actually process any
                        break;
                    }
                    Messages.log.deprecatedProperties();
                    if (connections == null) {
                        connections = new HashSet<>();
                    }
                    connections.add(name);
                }
            }

            Map<URI, AuthenticationConfiguration> overrides = null;

            if (! gotProviders && connections != null) {
                // now process each defined connection to synthesize compatibility provider URIs

                for (String connection : connections) {
                    final String connectionPrefix = REMOTE_CONNECTION_PREFIX + connection + ".";
                    final String connUserName = getEnvString(environment, connectionPrefix + EJB_USERNAME_KEY, null, true);
                    final String rawHostString = getEnvString(environment, connectionPrefix + EJB_HOST_KEY, null, true);
                    final String connHostName = NetworkUtils.formatPossibleIpv6Address(rawHostString);
                    final String connPassword = getEnvString(environment, connectionPrefix + EJB_PASSWORD_KEY, null, true);
                    final String connPasswordBase64 = getEnvString(environment, connectionPrefix + EJB_PASSWORD_BASE64_KEY, null, true);
                    final String connCallbackHandlerClass = getEnvString(environment, connectionPrefix + EJB_CALLBACK_HANDLER_CLASS_KEY, null, true);
                    final boolean sslEnabled = Boolean.parseBoolean(getEnvString(environment, connectionPrefix + CONNECT_OPTIONS + Options.SSL_ENABLED, Boolean.toString(isSsl), true));
                    final String protocol = getEnvString(environment, connectionPrefix + "protocol", sslEnabled ? "remote+https" : "remote+http", true);
                    final int connPort = getEnvInt(environment, connectionPrefix + EJB_PORT_KEY, sslEnabled ? 443 : 80, true);

                    CallbackHandler connCallbackHandler = null;

                    if (connCallbackHandlerClass != null) {
                        try {
                            final Class<?> clazz = Class.forName(connCallbackHandlerClass, true, classLoader);
                            connCallbackHandler = (CallbackHandler) clazz.newInstance();
                        } catch (ClassNotFoundException e) {
                            throw Messages.log.failedToLoadCallbackHandlerClass(e, connCallbackHandlerClass);
                        } catch (Exception e) {
                            throw Messages.log.failedToInstantiateCallbackHandlerInstance(e, connCallbackHandlerClass);
                        }
                    }

                    OptionMap connRemotingOptions = getOptionMap(environment, connectionPrefix + CONNECT_OPTIONS, Builder.class.getClassLoader());

                    if (connCallbackHandler != null || connUserName != null) {
                        // disable quiet local auth
                        connRemotingOptions = setQuietLocalAuth(connRemotingOptions, false);
                    }

                    if (connHostName != null) {
                        // we have a proper host, which is the minimum
                        final URI uri;
                        try {
                            uri = new URI(
                                protocol,
                                null,
                                connHostName,
                                connPort,
                                null,
                                null,
                                null
                            );
                        } catch (URISyntaxException e) {
                            throw Messages.log.invalidProviderGenerated(e);
                        }
                        if (connPassword != null || connPasswordBase64 != null || connCallbackHandler != null || connUserName != null) {
                            if (overrides == null) {
                                overrides = new HashMap<>();
                            }
                            AuthenticationConfiguration authConfig = RemotingOptions.mergeOptionsIntoAuthenticationConfiguration(connRemotingOptions, AuthenticationConfiguration.empty());

                            if (connUserName != null) {
                                authConfig = authConfig.useName(connUserName);
                            }
                            if (connCallbackHandler != null) {
                                authConfig = authConfig.useCallbackHandler(connCallbackHandler);
                            }
                            if (connPassword != null) {
                                authConfig = authConfig.usePassword(connPassword);
                            } else if (connPasswordBase64 != null) {
                                authConfig = authConfig.usePassword(CodePointIterator.ofString(connPasswordBase64).base64Decode().asUtf8String().drainToString());
                            }
                            overrides.putIfAbsent(uri, authConfig);
                        }
                        addProviderUri(uri);
                    }
                }
            }

            AuthenticationContext context = AuthenticationContext.empty();
            if (overrides != null) {
                // we have to add our config on a per-call basis, to overlay the inherited env
                for (Map.Entry<URI, AuthenticationConfiguration> entry : overrides.entrySet()) {
                    final URI key = entry.getKey();
                    final AuthenticationConfiguration configuration = entry.getValue();
                    final MatchRule rule = ruleFromLocation(key);
                    context = context.with(rule, configuration);
                }
            }

            if (overrideDefaultAuth) {
                // lastly, add the default rule
                context = context.with(MatchRule.ALL, globalAuthConf);
            }

            if (overrideDefaultAuth || overrides != null) {
                setAuthenticationContextSupplier(new FixedAuthenticationContextSupplier(context, ! overrideDefaultAuth));
            }

            return this;
        }

        private OptionMap getOptionMap(final Map<String, ?> environment, final String prefix, final ClassLoader classLoader) {
            OptionMap.Builder builder = OptionMap.builder();
            builder.set(Options.SASL_POLICY_NOANONYMOUS, false);
            for (String name : environment.keySet()) {
                if (name.startsWith(prefix)) {
                    final String optionName = name.substring(prefix.length());
                    final Option<?> option = Option.fromString(optionName, classLoader);
                    final Object valueObj = environment.get(name);
                    if (valueObj != null) builder.parse(option, valueObj.toString(), classLoader);
                }
            }
            return builder.getMap();
        }

        private static MatchRule ruleFromLocation(final URI uri) {
            MatchRule rule = MatchRule.ALL;
            final String scheme = uri.getScheme();
            if (scheme != null) {
                rule = rule.matchProtocol(scheme);
            }
            final String host = uri.getHost();
            if (host != null) {
                rule = rule.matchHost(host);
            }
            final int port = uri.getPort();
            if (port != -1) {
                rule = rule.matchPort(port);
            }
            final String path = uri.getPath();
            if (path != null && ! path.isEmpty()) {
                rule = rule.matchPath(path);
            }
            if (path == null && port == -1 && host == null) {
                final String schemeSpecificPart = uri.getSchemeSpecificPart();
                if (schemeSpecificPart != null) {
                    rule = rule.matchUrnName(schemeSpecificPart);
                }
            }
            return rule;
        }

        private static final String REMOTE_CONNECTION_PREFIX = "remote.connection.";
        private static final int REMOTE_CONNECTION_PREFIX_LEN = REMOTE_CONNECTION_PREFIX.length();
        private static final String CONNECT_OPTIONS_PREFIX = "jboss.naming.client.connect.options.";

        private static String connectionNameOf(String key) {
            if (key.startsWith(REMOTE_CONNECTION_PREFIX)) {
                int idx = key.indexOf('.', REMOTE_CONNECTION_PREFIX_LEN);
                final String connectionName = (idx == - 1 ? key.substring(REMOTE_CONNECTION_PREFIX_LEN) : key.substring(REMOTE_CONNECTION_PREFIX_LEN, idx)).trim();
                if (! connectionName.isEmpty()) {
                    return connectionName;
                }
            }
            return null;
        }

        private boolean populateProviderUris(Map<String, ?> env) throws ConfigurationException {
            Object urlString = env.get(Context.PROVIDER_URL);
            if (urlString != null) {
                String providerUriString = Expression.compile(urlString.toString(), Expression.Flag.LENIENT_SYNTAX).evaluateWithPropertiesAndEnvironment(false);
                if (! providerUriString.isEmpty()) {
                    final String[] urls = providerUriString.split(",");
                    for (String url : urls) {
                        URI providerUri;
                        try {
                            providerUri = new URI(url.trim());
                        } catch (URISyntaxException e) {
                            throw Messages.log.invalidProviderUri(e, url);
                        }
                        addProviderUri(providerUri);
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * Build the provider environment from a point-in-time snapshot of the values in this builder.
         *
         * @return the new provider environment
         */
        public ProviderEnvironment build() {
            return new ProviderEnvironment(this);
        }

        private static String getEnvString(final Map<String, ?> env, final String propertyName, final String defaultValue, final boolean expand) {
            final Object obj = env.get(propertyName);
            if (obj == null) {
                return defaultValue;
            }
            final String str = obj.toString();
            if (expand) {
                final Expression expression = Expression.compile(str, Expression.Flag.LENIENT_SYNTAX);
                return expression.evaluateWithPropertiesAndEnvironment(false);
            } else {
                return str.trim();
            }
        }

        private static int getEnvInt(final Map<String, ?> env, final String propertyName, final int defaultValue, final boolean expand) throws ConfigurationException {
            final Object obj = env.get(propertyName);
            if (obj == null) {
                return defaultValue;
            }
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            final String str = obj.toString();
            final String resultStr;
            if (expand) {
                final Expression expression = Expression.compile(str, Expression.Flag.LENIENT_SYNTAX);
                resultStr = expression.evaluateWithPropertiesAndEnvironment(false);
            } else {
                resultStr = str.trim();
            }
            try {
                return Integer.parseInt(resultStr);
            } catch (NumberFormatException e) {
                throw Messages.log.invalidNumericProperty(e, propertyName, resultStr);
            }
        }

        /**
         * Set the quiet local auth property to the given value if the user hasn't already set this property.
         *
         * @param optionMap the option map
         * @param useQuietAuth the value to set the quiet local auth property to
         * @return the option map with the quiet local auth property set to the given value if the user hasn't already set this property
         */
        private static OptionMap setQuietLocalAuth(final OptionMap optionMap, final boolean useQuietAuth) {
            final Sequence<Property> existingSaslProps = optionMap.get(Options.SASL_PROPERTIES);
            if (existingSaslProps != null) {
                for (Property prop : existingSaslProps) {
                    final String propKey = prop.getKey();
                    if (propKey.equals(LocalUserClient.QUIET_AUTH) || propKey.equals(LocalUserClient.LEGACY_QUIET_AUTH)) {
                        // quiet local auth property was already set, do not override it
                        return optionMap;
                    }
                }
                // set the quiet local auth property since it wasn't already set in SASL_PROPERTIES
                existingSaslProps.add(Property.of(LocalUserClient.QUIET_AUTH, Boolean.toString(useQuietAuth)));
                return optionMap;
            }
            // set the quiet local auth property since no SASL_PROPERTIES were set
            final OptionMap.Builder updatedOptionMapBuilder = OptionMap.builder().addAll(optionMap);
            updatedOptionMapBuilder.set(Options.SASL_PROPERTIES, Sequence.of(Property.of(LocalUserClient.QUIET_AUTH, Boolean.toString(useQuietAuth))));
            return updatedOptionMapBuilder.getMap();
        }

        private static ClassLoader secureGetContextClassLoader() {
            final ClassLoader contextClassLoader;
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                contextClassLoader = doPrivileged((PrivilegedAction<ClassLoader>) Builder::getContextClassLoader);
            } else {
                contextClassLoader = getContextClassLoader();
            }
            return contextClassLoader;
        }

        private static ClassLoader getContextClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

    }

    static final class FixedAuthenticationContextSupplier implements Supplier<AuthenticationContext> {
        private final AuthenticationContext context;
        private final boolean inherit;

        FixedAuthenticationContextSupplier(final AuthenticationContext context, final boolean inherit) {
            this.context = context;
            this.inherit = inherit;
        }

        public AuthenticationContext get() {
            return inherit ? context.with(AuthenticationContext.captureCurrent()) : context;
        }
    }
}
