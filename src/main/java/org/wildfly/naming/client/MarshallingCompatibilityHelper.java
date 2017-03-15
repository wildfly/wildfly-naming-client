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

import org.jboss.marshalling.ObjectResolver;

/**
 * A helper which allows naming providers and contexts to improve compatibility with older peers.
 * <p>
 * All methods on this interface should be {@code default} for easy forward compatibility.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface MarshallingCompatibilityHelper {

    /**
     * Get an object resolver for a marshalling or unmarshalling operation.
     *
     * @param transport the transport for requests (not {@code null})
     * @param request {@code true} if the resolver is being used for requests (client), or {@code false} for responses (server)
     * @return the object resolver, or {@code null} if none is provided by this helper
     */
    default ObjectResolver getObjectResolver(Transport transport, boolean request) {
        return null;
    }

}
