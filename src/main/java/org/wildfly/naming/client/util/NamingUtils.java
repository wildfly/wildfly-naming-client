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

package org.wildfly.naming.client.util;

import static org.wildfly.naming.client._private.Messages.log;

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingException;

import org.wildfly.naming.client.AbstractContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;

/**
 * Naming-related utilities.
 *
 * @author John Bailey
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NamingUtils {

    private NamingUtils() {
    }

    public static Name parentOf(final Name name) {
        return name.isEmpty() ? name : name.getPrefix(name.size() - 1);
    }

    public static CompositeName toCompositeName(final Name name) throws InvalidNameException {
        if (name instanceof CompositeName) {
            return (CompositeName) name;
        } else {
            final CompositeName compositeName = new CompositeName();
            compositeName.add(name.toString());
            return compositeName;
        }
    }

    public static Context emptyContext(final Hashtable<String, Object> environment) {
        return new AbstractContext(FastHashtable.of(environment)) {
            protected Object lookupNative(final Name name) throws NamingException {
                throw log.nameNotFound(name, name);
            }

            protected void bindNative(final Name name, final Object obj) throws NamingException {
                throw log.readOnlyContext();
            }

            protected void rebindNative(final Name name, final Object obj) throws NamingException {
                throw log.nameNotFound(name, name);
            }

            protected void unbindNative(final Name name) throws NamingException {
                throw log.nameNotFound(name, name);
            }

            protected void renameNative(final Name oldName, final Name newName) throws NamingException {
                throw log.nameNotFound(oldName, oldName);
            }

            protected CloseableNamingEnumeration<NameClassPair> listNative(final Name name) throws NamingException {
                if (name.isEmpty()) {
                    return CloseableNamingEnumeration.empty();
                } else {
                    throw log.nameNotFound(name, name);
                }
            }

            protected CloseableNamingEnumeration<Binding> listBindingsNative(final Name name) throws NamingException {
                if (name.isEmpty()) {
                    return CloseableNamingEnumeration.empty();
                } else {
                    throw log.nameNotFound(name, name);
                }
            }

            protected void destroySubcontextNative(final Name name) throws NamingException {
                throw log.nameNotFound(name, name);
            }

            protected Context createSubcontextNative(final Name name) throws NamingException {
                if (name.size() == 1) {
                    throw log.readOnlyContext();
                } else {
                    throw log.nameNotFound(name, name);
                }
            }

            protected Object lookupLinkNative(final Name name) throws NamingException {
                throw log.nameNotFound(name, name);
            }

            public void close() throws NamingException {
            }

            public String getNameInNamespace() throws NamingException {
                return "";
            }
        };
    }

    public static void safeClose(final Context context) {
        if (context != null) try {
            context.close();
        } catch (Throwable t) {
            log.contextCloseFailed(context, t);
        }
    }

    /**
     * Create a naming exception with a root cause.
     *
     * @param message the message
     * @param cause the cause, or {@code null} for none
     * @return the naming exception
     */
    public static NamingException namingException(final String message, final Throwable cause) {
        final NamingException namingException = new NamingException(message);
        if (cause != null) {
            namingException.setRootCause(cause);
        }
        return namingException;
    }
}
