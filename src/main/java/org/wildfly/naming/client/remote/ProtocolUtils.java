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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.wildfly.naming.client.MarshallingCompatibilityHelper;

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

    private static final List<MarshallingCompatibilityHelper> MARSHALLING_COMPATIBILITY_HELPERS;

    static {
        List<MarshallingCompatibilityHelper> list = new ArrayList<>();
        final ServiceLoader<MarshallingCompatibilityHelper> helpers = ServiceLoader.load(MarshallingCompatibilityHelper.class, ProtocolUtils.class.getClassLoader());
        final Iterator<MarshallingCompatibilityHelper> iterator = helpers.iterator();
        for (;;) try {
            if (! iterator.hasNext()) break;
            list.add(iterator.next());
        } catch (ServiceConfigurationError e) {}
        MARSHALLING_COMPATIBILITY_HELPERS = list;
    }

    static List<MarshallingCompatibilityHelper> getMarshallingCompatibilityHelpers() {
        return MARSHALLING_COMPATIBILITY_HELPERS;
    }
}
