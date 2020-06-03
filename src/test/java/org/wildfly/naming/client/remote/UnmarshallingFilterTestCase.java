/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

import javax.naming.CompositeName;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.naming.client.SimpleName;
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * Test server-side blacklisting of arbitrary classes sent by the client.
 *
 * @author Brian Stansberry
 */
public class UnmarshallingFilterTestCase {

    private static final String SERVER = "remote://localhost:9898";
    public TestServer server;

    @Before
    public void setup() throws Exception {
        server = new TestServer("test", "localhost", 9898);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop();
    }


    @Test
    public void testUnmarshallingFilter() throws NamingException {

        failLookupOnServer("unmarshal");

        try {
            bindOnServer("unmarshal", new IllegalArgumentException(), false);
            //Assert.fail("Should not be able to bind an IAE"); // failure doesn't result in an exception!
            failLookupOnServer("unmarshal");
        } catch (NamingException good) {
            // good
        }
        bindOnServer("unmarshal", new CompositeName("foo"), false);
        Assert.assertEquals(CompositeName.class, lookupOnServer("unmarshal").getClass());
        try {
            bindOnServer("unmarshal", new IllegalArgumentException(), true);
            //Assert.fail("Should not be able to rebind an IAE"); // failure doesn't result in an exception!
            Assert.assertEquals(CompositeName.class, lookupOnServer("unmarshal").getClass());
        } catch (NamingException good) {
            // good
        }
        bindOnServer("unmarshal", new CompositeName("bar"), true);
        Assert.assertEquals(CompositeName.class, lookupOnServer("unmarshal").getClass());

    }

    private void bindOnServer(String key, Object obj, boolean rebind) throws NamingException {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.provider.url", SERVER);
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);
        if (rebind) {
            context.rebind(key, obj);
        } else {
            context.bind(key, obj);
        }
    }

    private Object lookupOnServer(String key) throws NamingException {
        FastHashtable<String, Object> props = new FastHashtable<>();
        props.put("java.naming.provider.url", SERVER);
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);
        return context.lookup(key);
    }

    private void failLookupOnServer(String key) throws NamingException {
        try {
            lookupOnServer("unmarshal");
            Assert.fail();
        } catch (NameNotFoundException e) {
        }
    }
}
