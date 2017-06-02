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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class Protocol {
    private Protocol() {}

    static final int P_NAME = 0x00;
    static final int P_OBJECT = 0x01;
    static final int P_EXCEPTION = 0x02;
    static final int P_VOID = 0x03;
    static final int P_BINDING = 0x04;
    static final int P_CONTEXT = 0x05;
    static final int P_LIST = 0x06;

    static final int CMD_LOOKUP         = 0x01;
    static final int CMD_BIND           = 0x02;
    static final int CMD_REBIND         = 0x03;
    static final int CMD_LIST           = 0x04;
    static final int CMD_LIST_BINDINGS  = 0x05;
    static final int CMD_UNBIND         = 0x06;
    static final int CMD_RENAME         = 0x07;
    static final int CMD_CREATE_SUBCTX  = 0x08;
    static final int CMD_DESTROY_SUBCTX = 0x09;
    // unused                           = 0x0A;
    // unused                           = 0x0B;
    // unused                           = 0x0C;
    // unused                           = 0x0D;
    // unused                           = 0x0E;
    // unused                           = 0x0F;
    static final int CMD_LOOKUP_LINK    = 0x10;

    /*
     * Outcomes
     */
    static final int SUCCESS = 0x00;
    static final int FAILURE = 0x01;
}
