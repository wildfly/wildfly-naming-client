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

package org.wildfly.naming.client.util;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.naming.Context;
import javax.security.auth.x500.X500PrivateCredential;

import org.ietf.jgss.GSSCredential;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.GSSKerberosCredential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.X509CertificateChainPrivateCredential;
import org.wildfly.security.credential.X509CertificateChainPublicCredential;
import org.wildfly.security.password.Password;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * Various utilities dealing with environment properties.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class EnvironmentUtils {

    // Constants related to EJB remote connections in environment properties
    public static final String EJB_REMOTE_CONNECTIONS = "remote.connections";
    public static final String EJB_REMOTE_CONNECTION_PREFIX = "remote.connection.";
    public static final String EJB_REMOTE_CONNECTION_PROVIDER_PREFIX = "remote.connectionprovider.create.options.";
    public static final String CONNECT_OPTIONS = "connect.options.";
    public static final String EJB_HOST_KEY = "host";
    public static final String EJB_PORT_KEY = "port";
    public static final String EJB_CALLBACK_HANDLER_CLASS_KEY = "callback.handler.class";
    public static final String EJB_USERNAME_KEY = "username";
    public static final String EJB_PASSWORD_KEY = "password";
    public static final String EJB_PASSWORD_BASE64_KEY = "password.base64";

    private EnvironmentUtils() {
    }

    /**
     * Get the security credential, if any.  If an entry is present but not recognized, an empty credential set is
     * returned.
     *
     * @param env the environment (must not be {@code null})
     * @return the security credentials, or {@code null} if none was present
     */
    public static IdentityCredentials getSecurityCredentials(Hashtable<String, ?> env) {
        return getSecurityCredentials(env, Context.SECURITY_CREDENTIALS);
    }

    /**
     * Get the security credential, if any.  If an entry is present but not recognized, an empty credential set is
     * returned.
     *
     * @param env the environment (must not be {@code null})
     * @param propertyName the property name (must not be {@code null})
     * @return the security credentials, or {@code null} if none was present
     */
    public static IdentityCredentials getSecurityCredentials(final Hashtable<String, ?> env, final String propertyName) {
        Assert.checkNotNullParam("env", env);
        Assert.checkNotNullParam("propertyName", propertyName);
        final Object rawCredential = env.get(propertyName);
        if (rawCredential == null) {
            return null;
        } else if (rawCredential instanceof IdentityCredentials) {
            return (IdentityCredentials) rawCredential;
        } else if (rawCredential instanceof Collection<?>) {
            IdentityCredentials c = IdentityCredentials.NONE;
            for (Object item : (Collection<?>) rawCredential) {
                c = c.with(getSingleCredential(item));
            }
            return c;
        } else if (rawCredential instanceof Object[]) {
            IdentityCredentials c = IdentityCredentials.NONE;
            for (Object item : (Object[]) rawCredential) {
                c = c.with(getSingleCredential(item));
            }
            return c;
        } else {
            return getSingleCredential(rawCredential);
        }
    }

    private static IdentityCredentials getSingleCredential(Object rawCredential) {
        if (rawCredential == null) {
            return IdentityCredentials.NONE;
        } else if (rawCredential instanceof Credential) {
            return IdentityCredentials.NONE.withCredential((Credential) rawCredential);
        } else if (rawCredential instanceof GSSCredential) {
            return IdentityCredentials.NONE.withCredential(new GSSKerberosCredential((GSSCredential) rawCredential));
        } else if (rawCredential instanceof Password) {
            return IdentityCredentials.NONE.withCredential(new PasswordCredential((Password) rawCredential));
        } else if (rawCredential instanceof X509Certificate) {
            return IdentityCredentials.NONE.withCredential(new X509CertificateChainPublicCredential((X509Certificate) rawCredential));
        } else if (rawCredential instanceof X509Certificate[]) {
            return IdentityCredentials.NONE.withCredential(new X509CertificateChainPublicCredential((X509Certificate[]) rawCredential));
        } else if (rawCredential instanceof X500PrivateCredential) {
            final X500PrivateCredential credential = (X500PrivateCredential) rawCredential;
            return IdentityCredentials.NONE.withCredential(new X509CertificateChainPrivateCredential(credential.getPrivateKey(), credential.getCertificate()));
        } else if (rawCredential instanceof String) {
            return IdentityCredentials.NONE.withCredential(new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, ((String) rawCredential).toCharArray())));
        } else if (rawCredential instanceof char[]) {
            // todo: automatically decode to other credential types
            return IdentityCredentials.NONE.withCredential(new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, (char[]) rawCredential)));
        } else if (rawCredential instanceof byte[]) {
            // todo: automatically decode to other credential types
            return IdentityCredentials.NONE.withCredential(new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, new String((byte[]) rawCredential, StandardCharsets.UTF_8).toCharArray())));
        } else {
            return IdentityCredentials.NONE;
        }
    }

    /**
     * Get the name of the security realm, if any.
     *
     * @return the name of the security realm, or {@code null} if there is none or it is in an unrecognized format
     */
    public static String getSecurityRealmName(Hashtable<String, ?> env) {
        final Object rawRealm = env.get("java.naming.security.sasl.realm");
        return rawRealm instanceof String ? (String) rawRealm : null;
    }

    /**
     * Get the URL package prefixes defined in the given property name.  If no URL package prefixes are defined in the
     * given property name, then the default list is returned.  The returned list is mutable.
     *
     * @param env the environment (must not be {@code null})
     * @return the list of package prefixes
     */
    public static List<String> getURLPackagePrefixes(Hashtable<String, ?> env) {
        return getURLPackagePrefixes(env, Context.URL_PKG_PREFIXES);
    }

    /**
     * Get the URL package prefixes defined in the given property name.  If no URL package prefixes are defined in the
     * given property name, then the default list is returned.  The returned list is mutable.
     *
     * @param env the environment (must not be {@code null})
     * @param propertyName the property name to search (must not be {@code null})
     * @return the list of package prefixes
     */
    public static List<String> getURLPackagePrefixes(Hashtable<String, ?> env, String propertyName) {
        Assert.checkNotNullParam("env", env);
        Assert.checkNotNullParam("propertyName", propertyName);
        final ArrayList<String> list = new ArrayList<>();
        final Object prefixesObj = env.get(propertyName);
        if (prefixesObj instanceof String) {
            final String prefixes = (String) prefixesObj;
            if (! prefixes.isEmpty()) {
                final String[] split = prefixes.split(":");
                for (String s : split) {
                    if (! s.isEmpty()) {
                        list.add(s);
                    }
                }
            }
        }
        list.add("com.sun.jndi.url");
        return list;
    }

    /**
     * Compile the given collection of URL package prefixes into a string.
     *
     * @param prefixes the package prefixes (must not be {@code null})
     * @return the string, or {@code null} if there are no package prefixes (i.e. the corresponding property can be removed)
     */
    public static String compileURLPackagePrefixes(Collection<String> prefixes) {
        Assert.checkNotNullParam("prefixes", prefixes);
        final Iterator<String> iterator = prefixes.iterator();
        String firstName;
        while (iterator.hasNext()) {
            firstName = iterator.next();
            if (firstName != null && ! firstName.isEmpty() && ! firstName.equals("com.sun.jndi.url")) {
                String nextName;
                while (iterator.hasNext()) {
                    nextName = iterator.next();
                    if (nextName != null && ! nextName.isEmpty() && ! nextName.equals("com.sun.jndi.url")) {
                        StringBuilder b = new StringBuilder();
                        b.append(firstName);
                        b.append(':').append(nextName);
                        while (iterator.hasNext()) {
                            nextName = iterator.next();
                            if (nextName != null && ! nextName.isEmpty() && ! nextName.equals("com.sun.jndi.url")) {
                                b.append(':').append(nextName);
                            }
                        }
                        return b.toString();
                    }
                }
                return firstName;
            }
        }
        return null;
    }
}
