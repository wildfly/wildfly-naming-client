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

import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.wildfly.common.Assert;

/**
 * A naming enumeration which works with {@code try}-with-resources.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface CloseableNamingEnumeration<T> extends NamingEnumeration<T>, NamingCloseable {

    /**
     * Close the enumeration.
     *
     * @throws NamingException if closing the enumeration failed for some reason
     */
    void close() throws NamingException;

    /**
     * Create a naming enumeration from an iterable collection.
     *
     * @param iterable the iterable
     * @param <T> the element type
     * @return the enumeration
     */
    static <T> CloseableNamingEnumeration<T> fromIterable(Iterable<T> iterable) {
        Assert.checkNotNullParam("iterable", iterable);
        return fromIterator(iterable.iterator());
    }

    /**
     * Create a naming enumeration from a collection iterator.
     *
     * @param iterator the iterator
     * @param <T> the element type
     * @return the enumeration
     */
    static <T> CloseableNamingEnumeration<T> fromIterator(Iterator<T> iterator) {
        Assert.checkNotNullParam("iterator", iterator);
        return new CloseableNamingEnumeration<T>() {
            public T next() {
                return nextElement();
            }

            public boolean hasMore() {
                return hasMoreElements();
            }

            public void close() {
            }

            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            public T nextElement() {
                return iterator.next();
            }
        };
    }

    /**
     * Create a naming enumeration from a collection enumeration.
     *
     * @param enumeration the enumeration
     * @param <T> the element type
     * @return the enumeration
     */
    static <T> CloseableNamingEnumeration<T> fromEnumeration(Enumeration<T> enumeration) {
        Assert.checkNotNullParam("enumeration", enumeration);
        if (enumeration instanceof CloseableNamingEnumeration) {
            return (CloseableNamingEnumeration<T>) enumeration;
        } else if (enumeration instanceof NamingEnumeration) {
            return fromEnumeration((NamingEnumeration<T>) enumeration);
        }
        return new CloseableNamingEnumeration<T>() {
            public T next() {
                return nextElement();
            }

            public boolean hasMore() {
                return hasMoreElements();
            }

            public void close() {
            }

            public boolean hasMoreElements() {
                return enumeration.hasMoreElements();
            }

            public T nextElement() {
                return enumeration.nextElement();
            }
        };
    }

    /**
     * Create a closeable naming enumeration from a naming enumeration.
     *
     * @param enumeration the enumeration
     * @param <T> the element type
     * @return the enumeration
     */
    static <T> CloseableNamingEnumeration<T> fromEnumeration(NamingEnumeration<T> enumeration) {
        Assert.checkNotNullParam("enumeration", enumeration);
        if (enumeration instanceof CloseableNamingEnumeration) {
            return (CloseableNamingEnumeration<T>) enumeration;
        }
        return new CloseableNamingEnumeration<T>() {
            public T next() throws NamingException {
                return enumeration.next();
            }

            public boolean hasMore() throws NamingException {
                return enumeration.hasMore();
            }

            public void close() throws NamingException {
                enumeration.close();
            }

            public boolean hasMoreElements() {
                return enumeration.hasMoreElements();
            }

            public T nextElement() {
                return enumeration.nextElement();
            }
        };
    }

    CloseableNamingEnumeration<?> EMPTY = fromEnumeration(Collections.emptyEnumeration());

    @SuppressWarnings("unchecked")
    static <T> CloseableNamingEnumeration<T> empty() {
        return (CloseableNamingEnumeration<T>) EMPTY;
    }
}
