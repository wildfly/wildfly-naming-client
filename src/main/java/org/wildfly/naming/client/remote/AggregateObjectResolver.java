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

import java.util.ArrayList;

import org.jboss.marshalling.ObjectResolver;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class AggregateObjectResolver implements ObjectResolver {
    private final ArrayList<ObjectResolver> objectResolvers = new ArrayList<>();

    public Object readResolve(final Object replacement) {
        for (ObjectResolver resolver : objectResolvers) {
            final Object result = resolver.readResolve(replacement);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public Object writeReplace(final Object original) {
        for (ObjectResolver resolver : objectResolvers) {
            final Object result = resolver.writeReplace(original);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    AggregateObjectResolver add(ObjectResolver resolver) {
        objectResolvers.add(resolver);
        return this;
    }
}
