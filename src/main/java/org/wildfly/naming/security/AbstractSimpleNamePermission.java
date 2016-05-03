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

import javax.naming.InvalidNameException;

import org.wildfly.naming.client.SimpleName;
import org.wildfly.security.permission.AbstractNamedPermission;

/**
 * A base permission class which supports hierarchical {@link SimpleName}-based names with a simple root.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractSimpleNamePermission<This extends AbstractSimpleNamePermission<This>> extends AbstractNamedPermission<This> {

    private static final long serialVersionUID = - 1008993794534492843L;

    private final transient SimpleName simpleName;

    protected AbstractSimpleNamePermission(final String name) {
        super(name);
        try {
            this.simpleName = new SimpleName(name);
        } catch (InvalidNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected AbstractSimpleNamePermission(final SimpleName simpleName) {
        super(simpleName.toString());
        this.simpleName = simpleName;
    }

    public boolean impliesName(final This permission) {
        return permission != null && impliesName(permission.getSimpleName());
    }

    public boolean impliesName(final String name) {
        try {
            return impliesName(new SimpleName(name));
        } catch (InvalidNameException e) {
            return false;
        }
    }

    public boolean impliesName(SimpleName otherName) {
        return SimpleNamePermissions.impliesName(simpleName, otherName);
    }

    public boolean nameEquals(final String name) {
        try {
            return nameEquals(new SimpleName(name));
        } catch (InvalidNameException e) {
            return false;
        }
    }

    public boolean nameEquals(final SimpleName name) {
        return simpleName.equals(name);
    }

    protected SimpleName getSimpleName() {
        return simpleName;
    }
}
