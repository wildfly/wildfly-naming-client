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

import static org.wildfly.naming.client._private.Messages.log;

import java.util.Enumeration;
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
import org.wildfly.naming.client._private.Messages;

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

    /**
     * Create a CompositeName where each name segment is equal to the name segment in the source name.
     *
     * @param name the source name
     * @return a {@link CompositeName} where each name segment is equal to the name segment in the source name
     * @throws InvalidNameException if an error occurs while converting the source name to a {@link CompositeName}
     */
    public static CompositeName toDecomposedCompositeName(final Name name) throws InvalidNameException {
        if (name instanceof CompositeName) {
            return (CompositeName) name;
        } else {
            final CompositeName compositeName = new CompositeName();
            final Enumeration<String> enumeration = name.getAll();
            if (enumeration.hasMoreElements()) {
                int idx = 0;
                String item;
                do {
                    item = enumeration.nextElement();
                    if (item == null) {
                        throw Messages.log.invalidNullSegment(idx);
                    }
                    compositeName.add(item);
                    idx ++;
                } while (enumeration.hasMoreElements());
            }
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
