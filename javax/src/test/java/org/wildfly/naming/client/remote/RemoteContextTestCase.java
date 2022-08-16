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

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Hashtable;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.naming.client.ExhaustedDestinationsException;
import org.wildfly.naming.client.ProviderEnvironment;
import org.wildfly.naming.client.WildFlyInitialContextFactory;
import org.wildfly.naming.client.WildFlyRootContext;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * Tests various remote operations using RemoteContext
 *
 * @author Jason T. Greene
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2017 Red Hat inc.
 */
public class RemoteContextTestCase {
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

    private void bindOnServer(String server, String key, Object obj) throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", server);
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);
        context.bind(key, obj);
    }

    private Object lookupOnServer(String server, String key) throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();
        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", server);
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);
        return context.lookup(key);
    }

    private int getServerNotFoundCount(String SERVER1) throws Exception {
        return toInt(lookupOnServer(SERVER1, "$$$notFound$$$"));
    }

    private int toInt(Object o) {
        return Integer.class.cast(o);
    }

    @Test
    public void testOneWithNameAndOneWithout() throws Exception {
        final String SERVER1 = "remote://localhost:9898";
        final String SERVER2 = "remote://localhost:9896";

        TestServer server2 = new TestServer("test2", "localhost", 9896);
        server2.start();
        try {
            FastHashtable<String, Object> props = new FastHashtable<>();

            // Include all servers, so that retries are also tested
            props.put("java.naming.provider.url", SERVER1 + "," + SERVER2);
            props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
            WildFlyRootContext context = new WildFlyRootContext(props);

            bindOnServer(SERVER2, "hello", "there");
            for (int i = 0; i < 20; i++) {
                Assert.assertEquals("there", context.lookup("hello"));

            }

            bindOnServer(SERVER1, "quick", "fox");
            int total = 0;
            for (int i = 0; i < 10; i++) {
                int start1 = getServerNotFoundCount(SERVER1);
                int start2 = getServerNotFoundCount(SERVER2);
                Assert.assertEquals("there", context.lookup("hello"));
                Assert.assertEquals("fox", context.lookup("quick"));
                int count1 = getServerNotFoundCount(SERVER1) - start1;
                int count2 = getServerNotFoundCount(SERVER2) - start2;

                Assert.assertTrue(count1 < 2 && count1 >= 0);
                Assert.assertTrue(count2 < 2 && count2 >= 0);

                total += count1 + count2;
            }

            // Must be less than total invocations
            Assert.assertTrue(total < 20);

            Field providerEnvironment = context.getClass().getDeclaredField("providerEnvironment");
            providerEnvironment.setAccessible(true);
            ProviderEnvironment env = (ProviderEnvironment) providerEnvironment.get(context);
            Assert.assertEquals(0, env.getBlocklist().size());

        } finally {
            server2.stop();
        }
    }


    @Test
    public void testOneOfThreeUp() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:9897,remote://localhost:9896,remote://localhost:9898");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        for (int i = 0; i < 10; i++) {
            try {
                context.bind("hello", "there");
            } catch (NameAlreadyBoundException e) {
                //ignore
            }
            Assert.assertEquals("there", context.lookup("hello"));
        }

        Field providerEnvironment = context.getClass().getDeclaredField("providerEnvironment");
        providerEnvironment.setAccessible(true);
        ProviderEnvironment env = (ProviderEnvironment) providerEnvironment.get(context);

        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9897")));
        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9896")));
        Assert.assertEquals(2, env.getBlocklist().size());
    }

    @Test
    public void testNotFoundPreference() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:9897,remote://localhost:9896,remote://localhost:9898");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        for (int i = 0; i < 10; i++) {
            NameNotFoundException e = null;
            try {
                context.lookup("hello");
            } catch (NameNotFoundException o) {
                e = o;
            }

            Assert.assertNotNull(e);
        }

        Field providerEnvironment = context.getClass().getDeclaredField("providerEnvironment");
        providerEnvironment.setAccessible(true);
        ProviderEnvironment env = (ProviderEnvironment) providerEnvironment.get(context);

        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9897")));
        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9896")));
        Assert.assertEquals(2, env.getBlocklist().size());

        for (int i = 0; i < 10; i++) {
            NameNotFoundException e = null;
            try {
                // Disable blocklist to allow for random node distribution
                env.getBlocklist().clear();
                context.lookup("hello");
            } catch (NameNotFoundException o) {
                e = o;
            }

            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testNoneUp() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:9897,remote://localhost:9896,remote://localhost:9899");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        ExhaustedDestinationsException t= null;
        try {
            for (int i = 0; i < 10; i++) {
                context.bind("hello", "there");
            }
        } catch (ExhaustedDestinationsException o) {
            t = o;
        }
        Assert.assertNotNull(t);

        Field providerEnvironment = context.getClass().getDeclaredField("providerEnvironment");
        providerEnvironment.setAccessible(true);
        ProviderEnvironment env = (ProviderEnvironment) providerEnvironment.get(context);

        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9897")));
        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9896")));
        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9899")));
        Assert.assertEquals(3, env.getBlocklist().size());
    }

    @Test
    public void testComesBackUp() throws Exception {
        FastHashtable<String, Object> props = new FastHashtable<>();

        // Include all servers, so that retries are also tested
        props.put("java.naming.provider.url", "remote://localhost:9897,remote://localhost:9896");
        props.put("java.naming.factory.initial", WildFlyInitialContextFactory.class.getName());
        WildFlyRootContext context = new WildFlyRootContext(props);

        ExhaustedDestinationsException t = null;
        try {
            for (int i = 0; i < 10; i++) {
                context.bind("hello", "there");
            }
        } catch (ExhaustedDestinationsException o) {
            t = o;
        }
        Assert.assertNotNull(t);

        Field providerEnvironment = context.getClass().getDeclaredField("providerEnvironment");
        providerEnvironment.setAccessible(true);
        ProviderEnvironment env = (ProviderEnvironment) providerEnvironment.get(context);

        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9897")));
        Assert.assertTrue(env.getBlocklist().containsKey(new URI("remote://localhost:9896")));
        Assert.assertEquals(2, env.getBlocklist().size());

        t = null;
        try {
            context.bind("hello", "there");
        } catch (ExhaustedDestinationsException o) {
            t = o;
        }

        Assert.assertNotNull(t);

        // This case the blocklist should have prevented the attempt
        Assert.assertTrue(t.getSuppressed() == null || t.getSuppressed().length == 0);

        // Force expire
        env.getBlocklist().replace(new URI("remote://localhost:9896"), System.currentTimeMillis());
        TestServer server = new TestServer("test2", "localhost", 9896);
        server.start();

        // Should work  now
        Object result = null;
        try {
            context.bind("hello", "there");
            result = context.lookup("hello");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            server.stop();
        }

        // blocklist should be gone now
        Assert.assertEquals("there", result);
        Assert.assertFalse(env.getBlocklist().containsKey(new URI("remote://localhost:9896")));
    }

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
