/*
Copyright 2019 Red Hat, Inc.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package jboss.naming.remote.client;

import org.jboss.naming.remote.client.InitialContextFactory;
import org.junit.Assert;
import org.junit.Test;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Hashtable;

/**
 * Tests if jboss-naming-client.properties file is loaded when the legacy {@link InitialContextFactory} is used.
 * For more information visit https://issues.jboss.org/browse/WFNC-54
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public class LegacyClientPropertiesTestCase {

    @Test
    public void testLegacyClientProperties() throws NamingException {
        Hashtable<String, String> props = new Hashtable<>();
        props.put("java.naming.factory.initial", InitialContextFactory.class.getName());
        props.put("b", "2");
        InitialContext context = new InitialContext(props);
        Hashtable environment = context.getEnvironment();
        Assert.assertTrue("property 'a' should be loaded from jboss-naming-client.properties file",
                environment.containsKey("a")
        );
        Assert.assertEquals("1", environment.get("a"));
        Assert.assertTrue(environment.containsKey("b"));
        Assert.assertEquals("properties passed in to the InitialContext constructor should override values from jboss-naming-client.properties file",
                "2",
                environment.get("b")
        );
    }
    
}
