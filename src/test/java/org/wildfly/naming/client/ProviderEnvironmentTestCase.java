/*
Copyright 2023 Red Hat, Inc.
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
package org.wildfly.naming.client;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.interfaces.ClearPassword;

import javax.naming.Context;
import javax.naming.NamingException;
import java.io.IOException;
import java.security.Principal;

@RunWith(BMUnitRunner.class)
@BMScript(dir = "target/test-classes")
public class ProviderEnvironmentTestCase {

    static public Principal principal;
    static public CredentialSource credentialSource;

    @Before
    public void clear() {
        principal = null;
        credentialSource = null;
    }

    @Test
    public void testGlobalEnvironment() throws NamingException, IOException {

        FastHashtable<String, Object> environment = new FastHashtable<>();
        environment.put(Context.SECURITY_PRINCIPAL, "alice");
        environment.put(Context.SECURITY_CREDENTIALS, "topsecret");

        new WildFlyRootContext(environment);

        Assert.assertEquals("alice", principal.getName());
        Assert.assertEquals("topsecret", new String(((ClearPassword) credentialSource.getCredential(PasswordCredential.class).getPassword()).getPassword()));
    }
}
