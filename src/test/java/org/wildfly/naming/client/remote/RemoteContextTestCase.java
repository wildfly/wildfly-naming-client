/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

package org.wildfly.naming.client.remote;

import static junit.framework.Assert.assertEquals;

import java.util.Hashtable;

import javax.naming.Name;
import javax.naming.NamingException;

import org.junit.Test;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2017 Red Hat inc.
 */
public class RemoteContextTestCase {

    @Test
    public void testJavaSchemeWithJavaName() throws NamingException {
        // use null when the scheme is java
        RemoteContext context = new RemoteContext(null, null, new Hashtable<>());

        assertEqualNames(context, "jms/queue/test", "jms/queue/test");
        assertEqualNames(context, "java:/jms/queue/test", "java:/jms/queue/test");
    }

    private void assertEqualNames(RemoteContext context, String expectedString, String lookupString) throws NamingException {
        Name lookupName = createName(lookupString);
        Name realName = context.getRealName(lookupName);
        assertEquals(expectedString, realName.toString());
    }

    private Name createName(String name) throws NamingException {
        final WildFlyRootContext context = new WildFlyRootContext(new FastHashtable<>());
        return context.getNameParser(name).parse(name);

    }
}
