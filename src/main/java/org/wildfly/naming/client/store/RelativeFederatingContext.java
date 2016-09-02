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

package org.wildfly.naming.client.store;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingException;

import org.wildfly.common.Assert;
import org.wildfly.naming.client.AbstractContext;
import org.wildfly.naming.client.AbstractFederatingContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * A context which is a relative subcontext of a root context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class RelativeFederatingContext extends AbstractFederatingContext {

    private final AbstractContext rootContext;
    private final CompositeName prefix;

    public RelativeFederatingContext(final FastHashtable<String, Object> environment, final AbstractContext rootContext, final CompositeName prefix) {
        super(environment);
        Assert.checkNotNullParam("rootContext", rootContext);
        Assert.checkNotNullParam("prefix", prefix);
        this.rootContext = rootContext;
        this.prefix = prefix;
    }

    protected Object lookupNative(final Name name) throws NamingException {
        if (name.isEmpty()) {
            return new RelativeFederatingContext(new FastHashtable<>(getEnvironment()), rootContext, prefix);
        }
        return rootContext.lookup(decomposeName(getAbsoluteName(name)));
    }

    protected void bindNative(final Name name, final Object obj) throws NamingException {
        rootContext.bind(getAbsoluteName(name), obj);
    }

    protected void rebindNative(final Name name, final Object obj) throws NamingException {
        rootContext.rebind(getAbsoluteName(name), obj);
    }

    protected void unbindNative(final Name name) throws NamingException {
        rootContext.unbind(getAbsoluteName(name));
    }

    protected void renameNative(final Name oldName, final Name newName) throws NamingException {
        rootContext.rename(getAbsoluteName(oldName), getAbsoluteName(newName));
    }

    protected CloseableNamingEnumeration<NameClassPair> listNative(final Name name) throws NamingException {
        return rootContext.list(getAbsoluteName(name));
    }

    protected CloseableNamingEnumeration<Binding> listBindingsNative(final Name name) throws NamingException {
        return rootContext.listBindings(getAbsoluteName(name));
    }

    protected void destroySubcontextNative(final Name name) throws NamingException {
        rootContext.destroySubcontext(getAbsoluteName(name));
    }

    protected Context createSubcontextNative(final Name name) throws NamingException {
        return rootContext.createSubcontext(getAbsoluteName(name));
    }

    protected Object lookupLinkNative(final Name name) throws NamingException {
        return rootContext.lookupLink(getAbsoluteName(name));
    }

    public void close() {
    }

    public String getNameInNamespace() throws NamingException {
        return prefix.toString();
    }

    public NameParser getNativeNameParser() {
        return rootContext.getNativeNameParser();
    }

    private CompositeName getAbsoluteName(final Name suffix) throws InvalidNameException {
        final CompositeName compositeName = (CompositeName) prefix.clone();
        if (suffix instanceof CompositeName) {
            compositeName.addAll(suffix);
        } else {
            compositeName.add(suffix.toString());
        }
        return compositeName;
    }
}
