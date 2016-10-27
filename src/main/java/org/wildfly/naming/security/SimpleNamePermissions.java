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

package org.wildfly.naming.security;

import org.wildfly.naming.client.SimpleName;

/**
 * A class which supports using {@link SimpleName} instances for permission names.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SimpleNamePermissions {
    private SimpleNamePermissions() {
    }

    /**
     * Determine whether the first name implies the second name.
     *
     * @param myName the first name (must not be {@code null})
     * @param otherName the second name (must not be {@code null})
     * @return {@code true} if the first name implies the second name, {@code false} otherwise
     */
    public static boolean impliesName(SimpleName myName, SimpleName otherName) {
        final int mySize = myName.size();
        final int otherSize = otherName.size();
        final int len = Math.min(mySize, otherSize);
        for (int i = 0; i < len; i ++) {
            final String mySeg = myName.get(i);
            final String otherSeg = otherName.get(i);
            if (mySeg.equals("-")) {
                return true;
            }
            if (otherSeg.equals("-")) {
                return false;
            }
            if (mySeg.equals("*")) {
                continue;
            }
            if (! mySeg.equals(otherSeg)) {
                return false;
            }
        }
        return mySize == otherSize;
    }
}
