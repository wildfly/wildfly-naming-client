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
import org.wildfly.security.permission.AbstractActionSetPermission;
import org.wildfly.security.permission.AbstractPermissionCollection;
import org.wildfly.security.permission.SimplePermissionCollection;
import org.wildfly.security.util.StringEnumeration;

/**
 * An abstract base class for naming permissions which are based on {@link SimpleName} and which support separate
 * actions for each naming operation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractNamingPermission<This extends AbstractNamingPermission<This>> extends AbstractActionSetPermission<This> {

    private static final long serialVersionUID = - 2355041296353728334L;

    private static final StringEnumeration actionStrings = StringEnumeration.of(
        "bind",
        "rebind",
        "unbind",
        "lookup",
        "list",
        "listBindings",
        "createSubcontext",
        "destroySubcontext",
        "addNamingListener"
    );

    private static final int ACTION_BIND                = 0b000000001;
    private static final int ACTION_REBIND              = 0b000000010;
    private static final int ACTION_UNBIND              = 0b000000100;
    private static final int ACTION_LOOKUP              = 0b000001000;
    private static final int ACTION_LIST                = 0b000010000;
    private static final int ACTION_LIST_BINDINGS       = 0b000100000;
    private static final int ACTION_CREATE_SUBCTXT      = 0b001000000;
    private static final int ACTION_DESTROY_SUBCTXT     = 0b010000000;
    private static final int ACTION_ADD_NAMING_LISTENER = 0b100000000;

    private static final int ALL                        = 0b111111111;

    private final SimpleName simpleName;

    protected AbstractNamingPermission(final String name, final String actions) throws InvalidNameException {
        this(new SimpleName(name), actions);
    }

    protected AbstractNamingPermission(final SimpleName simpleName, final String actions) {
        super(simpleName.toString(), actions, actionStrings);
        this.simpleName = simpleName;
    }

    protected AbstractNamingPermission(final String name, final int actionBits) throws InvalidNameException {
        this(new SimpleName(name), actionBits);
    }

    protected AbstractNamingPermission(final SimpleName simpleName, final int actionBits) {
        super(simpleName.toString(), actionBits, actionStrings);
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

    protected This constructWithActionBits(final int actionBits) {
        return constructNew(simpleName, actionBits);
    }

    protected abstract This constructNew(SimpleName simpleName, int actionBits);

    public AbstractPermissionCollection newPermissionCollection() {
        return new SimplePermissionCollection(this);
    }
}
