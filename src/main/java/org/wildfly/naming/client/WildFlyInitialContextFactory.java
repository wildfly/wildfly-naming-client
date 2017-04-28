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

import java.util.Hashtable;
import java.util.Map;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.kohsuke.MetaInfServices;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * An initial context factory that constructs {@link WildFlyInitialContext} instances.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class WildFlyInitialContextFactory implements InitialContextFactory {

    /**
     * Construct a new instance.
     */
    public WildFlyInitialContextFactory() {
    }

    /**
     * Get a new initial context.
     *
     * @param environment the context environment
     * @return the initial context
     * @throws NamingException if constructing the initial context fails
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Context getInitialContext(final Hashtable<?, ?> environment) throws NamingException {
        return new WildFlyInitialContext(new FastHashtable<>((Map<String, Object>) (Map) environment));
    }
}
