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

import org.jboss.remoting3.Connection;
import org.wildfly.naming.client.Transport;

/**
 * The Remoting transport.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface RemoteTransport extends Transport {
    /**
     * Get the Remoting connection of the current request.
     *
     * @return the Remoting connection of the current request (not {@code null})
     */
    Connection getConnection();

    /**
     * Get the transport version of the current request.
     *
     * @return the transport version of the current request
     */
    int getVersion();
}
