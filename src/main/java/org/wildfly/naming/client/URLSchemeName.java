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

import java.util.Collection;
import java.util.Enumeration;
import java.util.regex.Pattern;

import javax.naming.InvalidNameException;
import javax.naming.Name;

import org.wildfly.common.Assert;
import org.wildfly.naming.client._private.Messages;

/**
 * A {@link Name} which includes an optional URL scheme attachment.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class URLSchemeName extends SimpleName {
    private static final long serialVersionUID = - 6031564938591212245L;

    private static final Pattern validPattern = Pattern.compile("^[A-Za-z][-A-Za-z0-9+]*$");

    private final String urlScheme;

    /**
     * Construct a new instance.
     *
     * @param urlScheme the URL scheme
     * @param enumeration the name components
     * @throws InvalidNameException if the name is not valid
     */
    public URLSchemeName(final String urlScheme, final Enumeration<String> enumeration) throws InvalidNameException {
        super(enumeration);
        Assert.checkNotNullParam("urlScheme", urlScheme);
        if (! validPattern.matcher(urlScheme).matches()) {
            throw Messages.log.invalidNameUrlScheme(urlScheme);
        }
        this.urlScheme = urlScheme;
    }

    /**
     * Construct a new instance.
     *
     * @param urlScheme the URL scheme
     * @param comps the name components
     * @throws InvalidNameException if the name is not valid
     */
    public URLSchemeName(final String urlScheme, final Collection<String> comps) throws InvalidNameException {
        super(comps);
        Assert.checkNotNullParam("urlScheme", urlScheme);
        if (! validPattern.matcher(urlScheme).matches()) {
            throw Messages.log.invalidNameUrlScheme(urlScheme);
        }
        this.urlScheme = urlScheme;
    }

    private URLSchemeName(final String urlScheme, final Collection<String> comps, @SuppressWarnings("unused") boolean ignored) {
        super(comps);
        this.urlScheme = urlScheme;
    }

    /**
     * Construct a new instance.
     *
     * @param name the name to parse (must include a URL component)
     * @throws InvalidNameException if the name is not valid
     */
    public URLSchemeName(final String name) throws InvalidNameException {
        this(name, colonIndex(name));
    }

    /**
     * Construct a new instance.
     *
     * @param urlScheme the URL scheme
     * @param subName the name segments to use
     * @throws InvalidNameException if the name is not valid
     */
    public URLSchemeName(final String urlScheme, final Name subName) throws InvalidNameException {
        this(urlScheme, subName.getAll());
    }

    private URLSchemeName(final String name, final int idx) throws InvalidNameException {
        this(idx == - 1 ? "" : name.substring(0, idx), name.substring(idx + 1));
    }

    private URLSchemeName(final String urlScheme, final String name) throws InvalidNameException {
        super(name);
        if (! validPattern.matcher(urlScheme).matches()) {
            throw Messages.log.invalidNameUrlScheme(urlScheme);
        }
        this.urlScheme = urlScheme;
    }

    private static int colonIndex(String segment) {
        int cp;
        boolean q = false, e = false, sq = false;
        for (int idx = 0; idx < segment.length(); idx = segment.offsetByCodePoints(idx, 1)) {
            cp = segment.codePointAt(idx);
            if (e) {
                // skip
            } else if (cp == '\\') {
                e = true;
            } else if (q) {
                if (cp == '"' && ! sq) {
                    q = false;
                } else if (cp == '\'' && sq) {
                    q = false;
                } else {
                    // skip
                }
            } else if (cp == '"') {
                q = true;
                sq = false;
            } else if (cp == '\'') {
                q = true;
                sq = true;
            } else if (cp == '/') {
                // not found before a legit /
                return -1;
            } else if (cp == ':') {
                return idx;
            }
        }
        return -1;
    }

    public static URLSchemeName fromName(Name name) throws InvalidNameException {
        Assert.checkNotNullParam("name", name);
        if (name instanceof URLSchemeName) {
            return (URLSchemeName) name;
        }
        if (name.isEmpty()) {
            return new URLSchemeName("");
        }
        final Enumeration<String> enumeration = name.getAll();
        final String first = enumeration.nextElement();
        final int idx = first.indexOf(':');
        if (idx == -1) {
            return new URLSchemeName("", name);
        } else {
            return new URLSchemeName(first.substring(0, idx), new Enumeration<String>() {
                boolean done;

                public boolean hasMoreElements() {
                    return ! done || enumeration.hasMoreElements();
                }

                public String nextElement() {
                    if (done) {
                        return enumeration.nextElement();
                    } else {
                        done = true;
                        return first.substring(idx + 1);
                    }
                }
            });
        }
    }

    /**
     * Clone this name instance.
     *
     * @return the cloned name
     */
    public URLSchemeName clone() {
        return new URLSchemeName(urlScheme, getRange(0, size()), false);
    }

    public URLSchemeName getPrefix(final int pos) {
        return new URLSchemeName(urlScheme, getRange(0, pos), false);
    }

    public SimpleName getSuffix(final int pos) {
        return pos == 0 ? clone() : super.getSuffix(pos);
    }

    public URLSchemeName addAll(final Name suffix) {
        super.addAll(suffix);
        return this;
    }

    public URLSchemeName addAll(final int pos, final Name name) {
        super.addAll(pos, name);
        return this;
    }

    public URLSchemeName add(final String comp) {
        super.add(comp);
        return this;
    }

    public URLSchemeName add(final int pos, final String comp) {
        super.add(pos, comp);
        return this;
    }

    /**
     * Get the URL scheme.
     *
     * @return the URL scheme (not {@code null})
     */
    public String getURLScheme() {
        return urlScheme;
    }

    public int compareTo(final Object obj) {
        return compareTo((URLSchemeName) obj);
    }

    public int compareTo(final SimpleName simpleName) {
        return compareTo((URLSchemeName) simpleName);
    }

    public int compareTo(final URLSchemeName schemeName) {
        int result = urlScheme.compareTo(schemeName.getURLScheme());
        return result != 0 ? result : super.compareTo(schemeName);
    }

    public boolean equals(final Object obj) {
        return obj instanceof URLSchemeName && equals((URLSchemeName) obj);
    }

    public boolean equals(final SimpleName obj) {
        return obj instanceof URLSchemeName && equals((URLSchemeName) obj);
    }

    public boolean equals(final URLSchemeName other) {
        return other != null && urlScheme.equals(other.urlScheme) && super.equals(other);
    }

    public int hashCode() {
        return super.hashCode() * 31 + urlScheme.hashCode();
    }

    public String toString() {
        return urlScheme + ':' + super.toString();
    }
}
