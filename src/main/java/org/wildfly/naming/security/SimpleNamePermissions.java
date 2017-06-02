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
