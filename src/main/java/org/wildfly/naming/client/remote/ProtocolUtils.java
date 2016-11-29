/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.io.IOException;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;

/**
 * Utilities related to the remote naming transport protocol.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
final class ProtocolUtils {

    public static final String NAMING = "naming";
    public static final byte[] NAMING_BYTES = { 'n', 'a', 'm', 'i', 'n', 'g' };

    private ProtocolUtils() {
    }

    public static int readId(final MessageInputStream stream, final int version) throws IOException {
        return version == 1 ? stream.readInt() : stream.readUnsignedShort();
    }

    public static void writeId(final MessageOutputStream stream, final int version, final int id) throws IOException {
        if (version == 1) {
            stream.writeInt(id);
        } else {
            stream.writeShort(id);
        }
    }

    public static Unmarshaller createUnmarshaller(MessageInputStream is, MarshallingConfiguration configuration) throws IOException {
        final Unmarshaller unmarshaller = Marshalling.getProvidedMarshallerFactory("river").createUnmarshaller(configuration);
        unmarshaller.start(Marshalling.createByteInput(is));
        return unmarshaller;
    }

    public static Marshaller createMarshaller(MessageOutputStream os, MarshallingConfiguration configuration) throws IOException {
        final Marshaller marshaller = Marshalling.getProvidedMarshallerFactory("river").createMarshaller(configuration);
        marshaller.start(Marshalling.createByteOutput(os));
        return marshaller;
    }

}
