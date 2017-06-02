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

import static org.wildfly.naming.client._private.Messages.log;
import static org.wildfly.naming.client.util.NamingUtils.safeClose;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingException;

import org.wildfly.common.Assert;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.naming.client.util.NamingUtils;

/**
 * A abstract federating context.  All text names are converted to composite names, and composite names are then used
 * to perform federating lookups.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractFederatingContext extends AbstractContext {

    protected AbstractFederatingContext(final FastHashtable<String, Object> environment) {
        super(environment);
    }

    public Object lookup(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        return lookup(new CompositeName(name));
    }

    public Object lookup(final Name name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name instanceof CompositeName) {
            if (name.isEmpty()) {
                return lookupNative(new SimpleName());
            }
            final String first = name.get(0);
            final Object next = lookup(getNativeNameParser().parse(first));
            if (name.size() == 1) {
                return next;
            } else if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    return context.lookup(name.getSuffix(1));
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            return lookupNative(name);
        }
    }

    public Object lookupLink(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        return lookupLink(new CompositeName(name));
    }

    public Object lookupLink(final Name name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name instanceof CompositeName) {
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                return lookupLink(firstName);
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    return context.lookupLink(name.getSuffix(1));
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            return lookupLinkNative(name);
        }
    }

    public void bind(final String name, final Object obj) throws NamingException {
        Assert.checkNotNullParam("name", name);
        bind(new CompositeName(name), obj);
    }

    public void bind(final Name name, final Object obj) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name.isEmpty()) {
            throw log.invalidEmptyName();
        }
        if (name instanceof CompositeName) {
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                bindNative(firstName, obj);
                return;
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    context.bind(name.getSuffix(1), obj);
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            bindNative(name, obj);
        }
    }

    public void rebind(final String name, final Object obj) throws NamingException {
        Assert.checkNotNullParam("name", name);
        rebind(new CompositeName(name), obj);
    }

    public void rebind(final Name name, final Object obj) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name.isEmpty()) {
            throw log.invalidEmptyName();
        }
        if (name instanceof CompositeName) {
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                rebindNative(firstName, obj);
                return;
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    context.rebind(name.getSuffix(1), obj);
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            rebindNative(name, obj);
        }
    }

    public void unbind(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        unbind(new CompositeName(name));
    }

    public void unbind(final Name name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name.isEmpty()) {
            throw log.invalidEmptyName();
        }
        if (name instanceof CompositeName) {
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                unbindNative(firstName);
                return;
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    context.unbind(name.getSuffix(1));
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            unbindNative(name);
        }
    }

    public void rename(final String oldName, final String newName) throws NamingException {
        Assert.checkNotNullParam("oldName", oldName);
        Assert.checkNotNullParam("newName", newName);
        rename(new CompositeName(oldName), new CompositeName(newName));
    }

    public void rename(final Name oldName, final Name newName) throws NamingException {
        Assert.checkNotNullParam("oldName", oldName);
        Assert.checkNotNullParam("newName", newName);
        if (oldName.isEmpty() || newName.isEmpty()) {
            throw log.invalidEmptyName();
        }
        if (oldName instanceof CompositeName) {
            final String oldFirst = oldName.get(0);
            final Name oldFirstName = getNativeNameParser().parse(oldFirst);
            final Name newFirstName;
            if (newName instanceof CompositeName) {
                newFirstName = getNativeNameParser().parse(newName.get(0));
            } else {
                newFirstName = newName;
            }
            if (oldName.size() == 1 && newName.size() == 1) {
                renameNative(oldFirstName, newFirstName);
                return;
            }
            if (! oldFirstName.equals(newFirstName)) {
                throw log.renameAcrossProviders(oldName, newName);
            }
            final Object next = lookupNative(oldFirstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    context.rename(oldName.getSuffix(1), newName.getSuffix(1));
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(oldFirst);
            }
            return;
        }
        Name target;
        if (newName instanceof CompositeName) {
            if (newName.size() == 1) {
                target = getNativeNameParser().parse(newName.get(0));
            } else {
                throw log.renameAcrossProviders(oldName, newName);
            }
        } else {
            target = newName;
        }
        renameNative(oldName, target);
    }

    public CloseableNamingEnumeration<NameClassPair> list(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        return list(new CompositeName(name));
    }

    public CloseableNamingEnumeration<NameClassPair> list(final Name name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name instanceof CompositeName) {
            if (name.isEmpty()) {
                return listNative(new SimpleName());
            }
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                return listNative(firstName);
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    return CloseableNamingEnumeration.fromEnumeration(context.list(name.getSuffix(1)));
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            return listNative(name);
        }
    }

    public CloseableNamingEnumeration<Binding> listBindings(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        return listBindings(new CompositeName(name));
    }

    public CloseableNamingEnumeration<Binding> listBindings(final Name name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name instanceof CompositeName) {
            if (name.isEmpty()) {
                return listBindingsNative(new SimpleName());
            }
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                return listBindingsNative(firstName);
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    return CloseableNamingEnumeration.fromEnumeration(context.listBindings(name.getSuffix(1)));
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            return listBindingsNative(name);
        }
    }

    public void destroySubcontext(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        destroySubcontext(new CompositeName(name));
    }

    public void destroySubcontext(final Name name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name.isEmpty()) {
            throw log.invalidEmptyName();
        }
        if (name instanceof CompositeName) {
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                destroySubcontext(firstName);
                return;
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    context.destroySubcontext(name.getSuffix(1));
                    return;
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            destroySubcontextNative(name);
        }
    }

    public Context createSubcontext(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        return createSubcontext(new CompositeName(name));
    }

    public Context createSubcontext(final Name name) throws NamingException {
        if (name.isEmpty()) {
            throw log.invalidEmptyName();
        }
        if (name instanceof CompositeName) {
            final String first = name.get(0);
            final Name firstName = getNativeNameParser().parse(first);
            if (name.size() == 1) {
                return createSubcontext(firstName);
            }
            final Object next = lookup(firstName);
            if (next instanceof Context) {
                final Context context = (Context) next;
                try {
                    return context.createSubcontext(name.getSuffix(1));
                } finally {
                    NamingUtils.safeClose(context);
                }
            } else {
                throw log.notContextInCompositeName(first);
            }
        } else {
            return createSubcontextNative(name);
        }
    }

    public String composeName(final String name, final String prefix) throws NamingException {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("prefix", prefix);
        final CompositeName first = new CompositeName(prefix);
        final CompositeName second = new CompositeName(name);
        first.addAll(second);
        return first.toString();
    }

    public Name composeName(final Name name, final Name prefix) throws NamingException {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("prefix", prefix);
        CompositeName compositeName;
        if (prefix instanceof CompositeName) {
            compositeName = (CompositeName) prefix.clone();
        } else {
            compositeName = new CompositeName();
            compositeName.add(prefix.toString());
        }
        if (name instanceof CompositeName) {
            compositeName.addAll(name);
        } else {
            compositeName.add(name.toString());
        }
        return compositeName;
    }

    public NameParser getNameParser(final String name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name.isEmpty()) {
            // shortcut
            return getNativeNameParser();
        }
        final CompositeName compositeName = new CompositeName(name);
        return getNameParser(compositeName);
    }

    public NameParser getNameParser(final Name name) throws NamingException {
        Assert.checkNotNullParam("name", name);
        if (name instanceof CompositeName) {
            return getNameParser((CompositeName) name);
        } else {
            // all in one namespace; it must be our name parser
            return getNativeNameParser();
        }
    }

    public NameParser getNameParser(final CompositeName compositeName) throws NamingException {
        if (compositeName.isEmpty()) {
            return getNativeNameParser();
        }
        final String first = compositeName.get(0);
        final Name nativeName = getNativeNameParser().parse(first);
        final Object obj = lookup(nativeName);
        if (obj instanceof Context) {
            final Context context = (Context) obj;
            try {
                return context.getNameParser(compositeName.getSuffix(1));
            } finally {
                safeClose(context);
            }
        } else {
            throw log.notContextInCompositeName(first);
        }
    }
}
