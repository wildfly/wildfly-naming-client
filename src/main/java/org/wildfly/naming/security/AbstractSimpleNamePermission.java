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
