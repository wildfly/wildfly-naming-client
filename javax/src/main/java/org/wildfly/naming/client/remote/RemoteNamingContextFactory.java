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

import javax.naming.Context;
import javax.naming.NamingException;

import org.kohsuke.MetaInfServices;
import org.wildfly.naming.client.NamingProvider;
import org.wildfly.naming.client.NamingContextFactory;
import org.wildfly.naming.client.ProviderEnvironment;
import org.wildfly.naming.client.util.FastHashtable;

/**
 * A naming context factory supporting JBoss Remoting-based transport.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MetaInfServices
public final class RemoteNamingContextFactory implements NamingContextFactory {

    /**
     * Construct a new instance.
     */
    public RemoteNamingContextFactory() {
    }

    public boolean supportsUriScheme(final NamingProvider namingProvider, final String nameScheme) {
        return namingProvider instanceof RemoteNamingProvider && (nameScheme == null || nameScheme.equals("java"));
    }

    public Context createRootContext(final NamingProvider namingProvider, final String nameScheme, final FastHashtable<String, Object> env, final ProviderEnvironment providerEnvironment) throws NamingException {
        // [WNFC-22] treat the java scheme as null
        return new RemoteContext((RemoteNamingProvider) namingProvider, "java".equals(nameScheme) ? null : nameScheme, env);
    }
}
