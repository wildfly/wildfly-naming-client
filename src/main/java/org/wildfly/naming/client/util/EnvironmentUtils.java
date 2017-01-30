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

package org.wildfly.naming.client.util;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Hashtable;

import javax.naming.Context;
import javax.security.auth.x500.X500PrivateCredential;

import org.ietf.jgss.GSSCredential;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.credential.GSSCredentialCredential;
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
            return IdentityCredentials.NONE.withCredential(new GSSCredentialCredential((GSSCredential) rawCredential));
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


}
