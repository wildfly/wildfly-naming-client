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

package org.wildfly.naming.client.store;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingException;

import org.wildfly.common.Assert;
import org.wildfly.naming.client.AbstractContext;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.SimpleName;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * A context which is a relative subcontext of a root context.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class RelativeContext extends AbstractContext {

    private final AbstractContext rootContext;
    private final SimpleName prefix;

    public RelativeContext(final FastHashtable<String, Object> environment, final AbstractContext rootContext, final SimpleName prefix) {
        super(environment);
        Assert.checkNotNullParam("rootContext", rootContext);
        Assert.checkNotNullParam("prefix", prefix);
        this.rootContext = rootContext;
        this.prefix = prefix;
    }

    protected Object lookupNative(final Name name) throws NamingException {
        if (name.isEmpty()) {
            return new RelativeContext(new FastHashtable<>(getEnvironment()), rootContext, prefix);
        }
        return rootContext.lookup(getAbsoluteName(name));
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

    private SimpleName getAbsoluteName(final Name suffix) {
        return prefix.clone().addAll(suffix);
    }
}
