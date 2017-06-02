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
