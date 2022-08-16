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
package org.wildfly.naming.client.remote;

import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.wildfly.naming.client.SimpleName;
import org.wildfly.naming.client._private.Messages;

/**
 * Partially implemented ultra-simple flat JNDI context
 *
 * @author Jason T. Greene
 */
class FlatMockContext implements Context {

    private ConcurrentMap<String, Object> entries = new ConcurrentHashMap<>();
    private final AtomicInteger notFoundCounter = new AtomicInteger(0);

    @Override
    public Object lookup(Name name) throws NamingException {
        if (name.size() <= 0) {
            return this;
        } else if (name.size() == 1) {
            String key = name.get(0);
            if ("$$$notFound$$$".equals(key)) {
                return notFoundCounter.get();
            }
            Object result = entries.get(key);
            if (result == null) {
                notFoundCounter.incrementAndGet();
                throw new NameNotFoundException();
            }
            return result;
        } else {
            throw Messages.log.notSupported();
        }
    }

    @Override
    public Object lookup(String name) throws NamingException {
        return lookup(new SimpleName(name));
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {
        bindInternal(name, obj, false);
    }

    private void bindInternal(Name name, Object obj, boolean overwrite) throws NamingException {
        if (name.size() <= 0) {
            throw new InvalidNameException();
        } else if (name.size() == 1) {
            if (overwrite) {
                entries.put(name.get(0), obj);
                return;
            }
            Object result = entries.putIfAbsent(name.get(0), obj);
            if (result != null) {
                throw new NameAlreadyBoundException();
            }
        } else {
            throw Messages.log.notSupported();
        }
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        bind(new SimpleName(name), obj);
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        bindInternal(name, obj, true);
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
        rebind(new SimpleName(name), obj);

    }

    @Override
    public void unbind(Name name) throws NamingException {
        if (name.size() <= 0) {
            throw new InvalidNameException();
        } else if (name.size() == 1) {
            entries.remove(name.get(0));
        } else {
            throw Messages.log.notSupported();
        }

    }

    @Override
    public void unbind(String name) throws NamingException {
        unbind(new SimpleName(name));
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        bind(newName, lookup(oldName));
        unbind(oldName);
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        rename(new SimpleName(oldName), new SimpleName(newName));
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public void destroySubcontext(String name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return null;
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        return null;
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        return null;
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public Object removeFromEnvironment(String propName) throws NamingException {
        throw Messages.log.notSupported();
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
       throw Messages.log.notSupported();
    }

    @Override
    public void close() throws NamingException {

    }

    @Override
    public String getNameInNamespace() throws NamingException {
        throw Messages.log.notSupported();
    }
}
