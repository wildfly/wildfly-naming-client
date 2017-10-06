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

import javax.naming.CommunicationException;

/**
 * Indicates that no more destinations are available to complete a naming
 * operation. Since this failure may represent multiple failures across mutliple
 * destinations, {@link #getSuppressed()} should be examined for a list of
 * underlying failures.
 *
 * @author Jason T. Greene
 */
public class ExhaustedDestinationsException extends CommunicationException {

    public ExhaustedDestinationsException() {
    }

    public ExhaustedDestinationsException(String explanation) {
        super(explanation);
    }
}
