/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.wildfly.naming.client;

import static java.security.AccessController.doPrivileged;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingException;

import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.naming.client.util.NamingUtils;

/**
 * A root context which locates providers based on the {@link Context#PROVIDER_URL} environment property as well as any
 * URL scheme which appears as a part of the JNDI name in the first segment.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class WildFlyRootContext extends AbstractFederatingContext {
    static {
        Version.getVersion();
    }

    private static final NameParser NAME_PARSER = URLSchemeName::new;

    private final ServiceLoader<NamingProvider> namingProviderServiceLoader;

    /**
     * Construct a new instance, searching the thread context class loader for providers.  If no context class loader is
     * set when this constructor is called, the class loader of this class is used.
     *
     * @param environment the environment to use (not copied)
     */
    public WildFlyRootContext(final FastHashtable<String, Object> environment) {
        this(environment, secureGetContextClassLoader());
    }

    /**
     * Construct a new instance, searching the given class loader for providers.
     *
     * @param environment the environment to use (not copied)
     * @param classLoader the class loader to search for providers
     */
    public WildFlyRootContext(final FastHashtable<String, Object> environment, final ClassLoader classLoader) {
        super(environment);
        namingProviderServiceLoader = ServiceLoader.load(NamingProvider.class, classLoader);
    }

    private static ClassLoader secureGetContextClassLoader() {
        final ClassLoader contextClassLoader;
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            contextClassLoader = doPrivileged((PrivilegedAction<ClassLoader>) WildFlyRootContext::getContextClassLoader);
        } else {
            contextClassLoader = getContextClassLoader();
        }
        return contextClassLoader == null ? WildFlyRootContext.class.getClassLoader() : contextClassLoader;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public NameParser getNativeNameParser() {
        return NAME_PARSER;
    }

    protected Object lookupNative(final Name name) throws NamingException {
        if (name.isEmpty()) {
            return new WildFlyRootContext(getEnvironment().clone());
        }
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        return getProviderContext(urlSchemeName).lookup(urlSchemeName);
    }

    protected void bindNative(final Name name, final Object obj) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        getProviderContext(urlSchemeName).bind(urlSchemeName, obj);
    }

    protected void rebindNative(final Name name, final Object obj) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        getProviderContext(urlSchemeName).rebind(urlSchemeName, obj);
    }

    protected void unbindNative(final Name name) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        getProviderContext(urlSchemeName).unbind(urlSchemeName);
    }

    protected void renameNative(final Name oldName, final Name newName) throws NamingException {
        final URLSchemeName oldUrlName = URLSchemeName.fromName(oldName);
        final URLSchemeName newUrlName = URLSchemeName.fromName(newName);
        getProviderContext(oldUrlName).rename(oldUrlName, newUrlName);
    }

    protected CloseableNamingEnumeration<NameClassPair> listNative(final Name name) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        return CloseableNamingEnumeration.fromEnumeration(getProviderContext(urlSchemeName).list(urlSchemeName));
    }

    protected CloseableNamingEnumeration<Binding> listBindingsNative(final Name name) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        return CloseableNamingEnumeration.fromEnumeration(getProviderContext(urlSchemeName).listBindings(urlSchemeName));
    }

    protected void destroySubcontextNative(final Name name) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        getProviderContext(urlSchemeName).destroySubcontext(urlSchemeName);
    }

    protected Context createSubcontextNative(final Name name) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        return getProviderContext(urlSchemeName).createSubcontext(urlSchemeName);
    }

    protected Object lookupLinkNative(final Name name) throws NamingException {
        final URLSchemeName urlSchemeName = URLSchemeName.fromName(name);
        return getProviderContext(urlSchemeName).lookupLink(urlSchemeName);
    }

    public void close() throws NamingException {
    }

    public String getNameInNamespace() throws NamingException {
        // always root
        return "";
    }

    private Context getProviderContext(URLSchemeName name) throws NamingException {
        final String nameScheme = name.getURLScheme();
        // get provider scheme
        final Object urlString = getEnvironment().get(PROVIDER_URL);
        URI providerUri;
        try {
            providerUri = urlString == null ? null : new URI(urlString.toString());
        } catch (URISyntaxException e) {
            throw Messages.log.invalidProviderUri(e, urlString);
        }
        final String providerScheme = providerUri == null ? null : providerUri.getScheme();
        // check for empty
        if (nameScheme.isEmpty() && (providerScheme == null || providerScheme.isEmpty())) {
            return NamingUtils.emptyContext(getEnvironment());
        }
        // get active naming providers
        final ServiceLoader<NamingProvider> loader = this.namingProviderServiceLoader;
        synchronized (loader) {
            final Iterator<NamingProvider> iterator = loader.iterator();
            for (;;) try {
                if (! iterator.hasNext()) break;
                final NamingProvider provider = iterator.next();
                if (provider.supportsUriScheme(providerScheme, nameScheme)) {
                    return provider.createRootContext(providerScheme, providerUri, getEnvironment());
                }
            } catch (ServiceConfigurationError error) {
                Messages.log.serviceConfigFailed(error);
            }
            throw Messages.log.nameNotFound(name, name);
        }

    }
}
