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
        return new WildFlyRootContext(new FastHashtable<>((Map<String, Object>) (Map) environment));
    }
}
