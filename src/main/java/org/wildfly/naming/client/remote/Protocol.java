/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
