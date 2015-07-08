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

import static java.lang.Integer.signum;
import static java.lang.Math.min;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;

import javax.naming.CompoundName;
import javax.naming.InvalidNameException;
import javax.naming.Name;

import org.wildfly.common.Assert;
import org.wildfly.naming.client._private.Messages;

/**
 * A simple compound name which uses left-to-right parsing, {@code /} separators, and simple quoting rules.  This class
 * is designed to perform better and have lower overhead than {@link CompoundName}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class SimpleName implements Name, Serializable {
    private static final long serialVersionUID = - 91630190623885257L;

    private final ArrayList<String> segments;

    public SimpleName() {
        segments = new ArrayList<>();
    }

    public SimpleName(String name) throws InvalidNameException {
        this(parse(name));
    }

    public SimpleName(final Collection<String> segments) {
        this(new ArrayList<>(segments));
    }

    public SimpleName(final Name name) {
        this(name.getAll());
    }

    private SimpleName(final ArrayList<String> literalSegments) {
        for (int i = 0; i < literalSegments.size(); i++) {
            if (literalSegments.get(i) == null) {
                throw Messages.log.invalidNullSegment(i);
            }
        }
        this.segments = literalSegments;
    }

    public SimpleName(final Enumeration<String> enumeration) {
        this();
        if (enumeration.hasMoreElements()) {
            final ArrayList<String> segments = this.segments;
            int idx = 0;
            String item;
            do {
                item = enumeration.nextElement();
                if (item == null) {
                    throw Messages.log.invalidNullSegment(idx);
                }
                segments.add(item);
                idx ++;
            } while (enumeration.hasMoreElements());
        }
    }

    private static ArrayList<String> parse(String name) throws InvalidNameException {
        final ArrayList<String> segments = new ArrayList<>();
        final int length = name.length();
        final StringBuilder b = new StringBuilder();
        int ch;
        boolean q = false, e = false, sq = false;
        for (int i = 0; i < length; i = name.offsetByCodePoints(i, 1)) {
            ch = name.codePointAt(i);
            if (e) {
                e = false;
                b.appendCodePoint(ch);
            } else if (q) {
                if (sq && ch == '\'') {
                    q = false;
                } else if (! sq && ch == '"') {
                    q = false;
                } else if (ch == '\\') {
                    e = true;
                } else {
                    b.appendCodePoint(ch);
                }
            } else if (ch == '/') {
                segments.add(b.toString());
                b.setLength(0);
            } else if (ch == '"') {
                q = true;
                sq = false;
            } else if (ch == '\'') {
                q = true;
                sq = true;
            } else if (ch == '\\') {
                e = true;
            }
        }
        if (q) {
            throw Messages.log.missingCloseQuote(sq ? '\'' : '"', name);
        } else if (e) {
            throw Messages.log.missingEscape(name);
        }
        return segments;
    }

    public int compareTo(final Object obj) {
        return compareTo((SimpleName) obj);
    }

    public int compareTo(final SimpleName simpleName) {
        int result;
        final ArrayList<String> segments = this.segments;
        final ArrayList<String> theirSegments = simpleName.segments;
        final int theirSize = theirSegments.size();
        final int ourSize = segments.size();
        for (int i = 0; i < min(ourSize, theirSize); i ++) {
            result = segments.get(i).compareTo(theirSegments.get(i));
            if (result != 0) {
                return result;
            }
        }
        return signum(ourSize - theirSize);
    }

    public boolean equals(final Object obj) {
        return obj != null && obj.getClass() == getClass() && equals((SimpleName) obj);
    }

    public boolean equals(final SimpleName simpleName) {
        return simpleName != null && simpleName.getClass() == getClass() && segments.equals(simpleName.segments);
    }

    public int hashCode() {
        return segments.hashCode();
    }

    public int size() {
        return segments.size();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public Enumeration<String> getAll() {
        return Collections.enumeration(segments);
    }

    public String toString() {
        final Iterator<String> iterator = segments.iterator();
        if (iterator.hasNext()) {
            StringBuilder b = new StringBuilder();
            appendSegment(b, iterator.next());
            while (iterator.hasNext()) {
                b.append('/');
                appendSegment(b, iterator.next());
            }
            return b.toString();
        } else {
            return "";
        }
    }

    private static void appendSegment(final StringBuilder b, final String segment) {
        int cp;
        for (int i = 0; i < segment.length(); i = segment.offsetByCodePoints(i, 1)) {
            cp = segment.codePointAt(i);
            if (cp == '/' || cp == '\'' || cp == '"') {
                b.append('\\');
            }
            b.appendCodePoint(cp);
        }
    }

    public String get(final int pos) {
        final ArrayList<String> segments = this.segments;
        if (pos < 0 || pos >= segments.size()) {
            throw Messages.log.nameIndexOutOfBounds(pos);
        }
        return segments.get(pos);
    }

    public Collection<String> getRange(final int start, final int end) {
        return segments.subList(start, end);
    }

    public SimpleName getPrefix(final int pos) {
        return new SimpleName(getRange(0, pos));
    }

    public SimpleName getSuffix(final int pos) {
        return new SimpleName(getRange(pos, size()));
    }

    public boolean startsWith(final Name name) {
        final ArrayList<String> segments = this.segments;
        final int size = name.size();
        if (size > segments.size()) {
            return false;
        }
        for (int i = 0; i < size; i ++) {
            if (! segments.get(i).equals(name.get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean endsWith(final Name name) {
        final ArrayList<String> segments = this.segments;
        final int size = name.size();
        final int ourSize = segments.size();
        if (size > ourSize) {
            return false;
        }
        final int offs = ourSize - size;
        for (int i = 0; i < size; i ++) {
            if (! segments.get(i + offs).equals(name.get(i))) {
                return false;
            }
        }
        return true;
    }

    public SimpleName addAll(final Name suffix) {
        if (suffix instanceof SimpleName) {
            segments.addAll(((SimpleName) suffix).segments);
            return this;
        }
        final ArrayList<String> segments = this.segments;
        final int size = suffix.size();
        final int ourSize = segments.size();
        segments.ensureCapacity(ourSize + size);
        for (int i = 0; i < size; i ++) {
            add(suffix.get(i));
        }
        return this;
    }

    public SimpleName addAll(final int pos, final Name name) {
        if (name instanceof SimpleName) {
            segments.addAll(pos, ((SimpleName) name).segments);
            return this;
        }
        final ArrayList<String> segments = this.segments;
        final int size = name.size();
        final int ourSize = segments.size();
        segments.ensureCapacity(ourSize + size);
        for (int i = 0; i < size; i ++) {
            add(pos + i, name.get(i));
        }
        return this;
    }

    public SimpleName add(final String comp) {
        Assert.checkNotNullParam("comp", comp);
        segments.add(comp);
        return this;
    }

    public SimpleName add(final int pos, final String comp) {
        Assert.checkNotNullParam("comp", comp);
        segments.add(pos, comp);
        return this;
    }

    public String remove(final int pos) {
        return segments.remove(pos);
    }

    public SimpleName clone() {
        return new SimpleName(segments);
    }
}
