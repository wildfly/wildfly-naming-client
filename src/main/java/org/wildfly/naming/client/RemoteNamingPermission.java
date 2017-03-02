package org.wildfly.naming.client;

import org.wildfly.security.permission.AbstractBooleanPermission;

/**
 * Represents permission to invoke naming operations remotely
 *
 * @author Stuart Douglas
 */
public class RemoteNamingPermission extends AbstractBooleanPermission<RemoteNamingPermission> {

    /**
     * Construct a new instance.
     */
    public RemoteNamingPermission() {
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     */
    public RemoteNamingPermission(@SuppressWarnings("unused") final String name) {
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     * @param actions ignored
     */
    public RemoteNamingPermission(@SuppressWarnings("unused") final String name, @SuppressWarnings("unused") final String actions) {
    }

    private static final RemoteNamingPermission INSTANCE = new RemoteNamingPermission();

    /**
     * Get the instance of this class.
     *
     * @return the instance of this class
     */
    public static RemoteNamingPermission getInstance() {
        return INSTANCE;
    }
}
