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

import org.jboss.marshalling.ContextClassResolver;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.util.MessageTracker;
import org.wildfly.naming.client.Version;
import org.wildfly.naming.client._private.Messages;

import javax.management.RuntimeMBeanException;
import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.wildfly.naming.client.remote.ProtocolUtils.*;
import static org.wildfly.naming.client.remote.TCCLUtils.getAndSetSafeTCCL;
import static org.wildfly.naming.client.remote.TCCLUtils.resetTCCL;
import static org.xnio.IoUtils.safeClose;

/**
 * The server side of the remote naming transport protocol.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class RemoteServerTransport implements RemoteTransport {
    private final MarshallingConfiguration configuration;
    private final Channel channel;
    private final int version;
    private final Context localContext;
    private final MessageTracker messageTracker;

    static {
        Version.getVersion();
    }

    RemoteServerTransport(final Channel channel, final int version, final MessageTracker messageTracker,
                          final Context localContext, final Function<String, Boolean> classResolverFilter) {
        this.channel = channel;
        this.version = version;
        this.messageTracker = messageTracker;
        this.localContext = localContext;
        this.configuration = new MarshallingConfiguration();
        configuration.setVersion(version >= 2 ? 4 : 2);
        configuration.setClassResolver(classResolverFilter != null ? new FilterClassResolver(classResolverFilter) : new ContextClassResolver());
        EENamespaceInteroperability.handleInteroperability(configuration, version);
    }

    public Connection getConnection() {
        return channel.getConnection();
    }

    public int getVersion() {
        return version;
    }

    MarshallingConfiguration getConfiguration() {
        return configuration;
    }

    public void start() {
        channel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                final ClassLoader oldCL = getAndSetSafeTCCL();
                try {
                    safeClose(channel);
                } finally {
                    resetTCCL(oldCL);
                }
            }

            public void handleEnd(final Channel channel) {
                final ClassLoader oldCL = getAndSetSafeTCCL();
                try {
                    safeClose(channel);
                } finally {
                    resetTCCL(oldCL);
                }
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                final ClassLoader oldCL = getAndSetSafeTCCL();
                try (MessageInputStream mis = message) {
                    final byte messageId = mis.readByte();
                    final int id = readId(mis, version);
                    try {
                        switch (messageId) {
                            case Protocol.CMD_LOOKUP:
                                handleLookup(mis, messageId, id, false);
                                break;
                            case Protocol.CMD_LOOKUP_LINK:
                                handleLookup(mis, messageId, id, true);
                                break;
                            case Protocol.CMD_BIND:
                                handleBind(mis, messageId, id, false);
                                break;
                            case Protocol.CMD_REBIND:
                                handleBind(mis, messageId, id, true);
                                break;
                            case Protocol.CMD_UNBIND:
                                handleUnbind(mis, messageId, id);
                                break;
                            case Protocol.CMD_RENAME:
                                handleRename(mis, messageId, id);
                                break;
                            case Protocol.CMD_DESTROY_SUBCTX:
                                handleDestroySubcontext(mis, messageId, id);
                                break;
                            case Protocol.CMD_CREATE_SUBCTX:
                                handleCreateSubcontext(mis, messageId, id);
                                break;
                            case Protocol.CMD_LIST:
                                handleList(mis, messageId, id);
                                break;
                            case Protocol.CMD_LIST_BINDINGS:
                                handleListBindings(mis, messageId, id);
                                break;
                            default:
                                throw Messages.log.unrecognizedMessageId();
                        }
                    } catch (Throwable t) {
                        if (id != 0x00) {
                            Exception response;
                            if (t instanceof IOException || t instanceof RuntimeMBeanException) {
                                response = (Exception) t;
                            } else {
                                response = new IOException("Internal server error.");
                                Messages.log.unexpectedError(t);
                            }
                            sendException(response, messageId, id);
                        } else {
                            Messages.log.nullCorrelationId(t);
                        }
                    }
                } catch (Throwable t) {
                    Messages.log.unexpectedError(t);
                } finally {
                    channel.receiveMessage(this);
                    resetTCCL(oldCL);
                }
            }
        });
    }

    void handleLookup(final MessageInputStream message, final int messageId, final int id, final boolean preserveLinks) throws IOException {
        final Object result;
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    final int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name name = unmarshaller.readObject(Name.class);
                    if (preserveLinks) {
                        result = localContext.lookupLink(name);
                    } else {
                        result = localContext.lookup(name);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            } else {
                mis.readInt(); // consume authId
                final String name = mis.readUTF();
                if (preserveLinks) {
                    result = localContext.lookupLink(name);
                } else {
                    result = localContext.lookup(name);
                }
            }
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        writeSuccessResponse(messageId, id, result);
    }

    void handleBind(final MessageInputStream message, final int messageId, final int id, final boolean rebind) throws IOException {
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name name = unmarshaller.readObject(Name.class);
                    parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_OBJECT) {
                        Messages.log.unexpectedParameterType(Protocol.P_OBJECT, parameterType);
                    }
                    final Object object = unmarshaller.readObject();
                    if (rebind) {
                        localContext.rebind(name, object);
                    } else {
                        localContext.bind(name, object);
                    }
                }
            } else {
                mis.readInt(); // consume authId
                final String name = mis.readUTF();
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    final Object object = unmarshaller.readObject();
                    if (rebind) {
                        localContext.rebind(name, object);
                    } else {
                        localContext.bind(name, object);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        writeSuccessResponse(messageId, id);
    }

    void handleUnbind(final MessageInputStream message, final int messageId, final int id) throws IOException {
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    final int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name name = unmarshaller.readObject(Name.class);
                    localContext.unbind(name);
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            } else {
                mis.readInt(); // consume authId
                final String name = mis.readUTF();
                localContext.unbind(name);
            }
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        writeSuccessResponse(messageId, id);
    }

    void handleRename(final MessageInputStream message, final int messageId, final int id) throws IOException {
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name oldName = unmarshaller.readObject(Name.class);
                    parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name newName = unmarshaller.readObject(Name.class);
                    localContext.rename(oldName, newName);
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            } else {
                mis.readInt(); // consume authId
                final String oldName = mis.readUTF();
                final String newName = mis.readUTF();
                localContext.rename(oldName, newName);
            }
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        writeSuccessResponse(messageId, id);
    }

    void handleDestroySubcontext(final MessageInputStream message, final int messageId, final int id) throws IOException {
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    final int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name name = unmarshaller.readObject(Name.class);
                    localContext.destroySubcontext(name);
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            } else {
                mis.readInt(); // consume authId
                final String name = mis.readUTF();
                localContext.destroySubcontext(name);
            }
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        writeSuccessResponse(messageId, id);
    }

    void handleCreateSubcontext(final MessageInputStream message, final int messageId, final int id) throws IOException {
        final Object result;
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    final int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name name = unmarshaller.readObject(Name.class);
                    result = localContext.createSubcontext(name);
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            } else {
                mis.readInt(); // consume authId
                final String name = mis.readUTF();
                result = localContext.createSubcontext(name);
            }
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        writeSuccessResponse(messageId, id, result);
    }

    void handleList(final MessageInputStream message, final int messageId, final int id) throws IOException {
        final NamingEnumeration<NameClassPair> results;
        final List<NameClassPair> resultList;
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    final int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name name = unmarshaller.readObject(Name.class);
                    results = localContext.list(name);
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            } else {
                mis.readInt(); // consume authId
                final String name = mis.readUTF();
                results = localContext.list(name);
            }
            resultList = new ArrayList<>();
            while (results.hasMore()) {
                resultList.add(results.next());
            }
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        try (MessageOutputStream mos = messageTracker.openMessageUninterruptibly()) {
            mos.writeByte(messageId);
            writeId(mos, version, id);
            mos.writeByte(Protocol.SUCCESS);
            if (version == 1) {
                mos.writeByte(Protocol.P_LIST);
                mos.writeInt(resultList.size());
                try (Marshaller marshaller = createMarshaller(mos, configuration)) {
                    for (NameClassPair nameClassPair : resultList) {
                        marshaller.writeObject(nameClassPair);
                    }
                }
            } else {
                mos.writeInt(resultList.size());
                for (NameClassPair nameClassPair : resultList) {
                    mos.writeUTF(nameClassPair.getName());
                    mos.writeUTF(nameClassPair.getClassName());
                }
            }
        }
    }

    void handleListBindings(final MessageInputStream message, final int messageId, final int id) throws IOException {
        final NamingEnumeration<Binding> results;
        final List<Binding> resultList;
        try (MessageInputStream mis = message) {
            if (version == 1) {
                try (Unmarshaller unmarshaller = createUnmarshaller(mis, configuration)) {
                    final int parameterType = unmarshaller.readUnsignedByte();
                    if (parameterType != Protocol.P_NAME) {
                        Messages.log.unexpectedParameterType(Protocol.P_NAME, parameterType);
                    }
                    final Name name = unmarshaller.readObject(Name.class);
                    results = localContext.listBindings(name);
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            } else {
                mis.readInt(); // consume authId
                final String name = mis.readUTF();
                results = localContext.listBindings(name);
            }
            resultList = new ArrayList<>();
            while (results.hasMore()) {
                resultList.add(results.next());
            }
        } catch (NamingException e) {
            writeExceptionResponse(e, messageId, id);
            return;
        }
        try (MessageOutputStream mos = messageTracker.openMessageUninterruptibly()) {
            mos.writeByte(messageId);
            writeId(mos, version, id);
            mos.writeByte(Protocol.SUCCESS);
            if (version == 1) {
                mos.writeByte(Protocol.P_LIST);
            }
            mos.writeInt(resultList.size());
            try (Marshaller marshaller = createMarshaller(mos, configuration)) {
                for (Binding binding : resultList) {
                    if (binding.getObject() instanceof Context) {
                        marshaller.writeByte(Protocol.P_CONTEXT);
                        marshaller.writeUTF(binding.getName());
                    } else {
                        marshaller.writeByte(Protocol.P_BINDING);
                        marshaller.writeObject(binding);
                    }
                }
            }
        }
    }

    private void writeSuccessResponse(final int messageId, final int id) throws IOException {
        try (MessageOutputStream mos = messageTracker.openMessageUninterruptibly()) {
            mos.writeByte(messageId);
            writeId(mos, version, id);
            mos.writeByte(Protocol.SUCCESS);
        }
    }

    private void writeSuccessResponse(final int messageId, final int id, final Object result) throws IOException {
        try (MessageOutputStream mos = messageTracker.openMessageUninterruptibly()) {
            mos.writeByte(messageId);
            writeId(mos, version, id);
            mos.writeByte(Protocol.SUCCESS);
            if (result instanceof Context) {
                mos.writeByte(Protocol.P_CONTEXT);
            } else {
                mos.writeByte(Protocol.P_OBJECT);
                try (Marshaller marshaller = createMarshaller(mos, configuration)) {
                    marshaller.writeObject(result);
                }
            }

        }
    }

    private void writeExceptionResponse(final Exception e, final int messageId, final int id) throws IOException {
        try (MessageOutputStream mos = messageTracker.openMessageUninterruptibly()) {
            mos.writeByte(messageId);
            writeId(mos, version, id);
            mos.writeByte(Protocol.FAILURE);
            mos.writeByte(Protocol.P_EXCEPTION);
            try (Marshaller marshaller = createMarshaller(mos, configuration)) {
                marshaller.writeObject(e);
            }
        }
    }

    private void sendException(final Exception e, final int messageId, final int id) {
        try {
            writeExceptionResponse(e, messageId, id);
        } catch (IOException ioe) {
            Messages.log.failedToSendExceptionResponse(ioe);
        }
    }

    private static class FilterClassResolver extends ContextClassResolver {
        private final Function<String, Boolean> filter;

        private FilterClassResolver(Function<String, Boolean> filter) {
            this.filter = filter;
        }

        @Override
        public Class<?> resolveClass(Unmarshaller unmarshaller, String name, long serialVersionUID) throws IOException, ClassNotFoundException {
            checkFilter(name);
            return super.resolveClass(unmarshaller, name, serialVersionUID);
        }

        @Override
        public Class<?> resolveProxyClass(Unmarshaller unmarshaller, String[] interfaces) throws IOException, ClassNotFoundException {
            for (String name : interfaces) {
                checkFilter(name);
            }
            return super.resolveProxyClass(unmarshaller, interfaces);
        }

        private void checkFilter(String className) throws InvalidClassException {
            if (filter.apply(className) != Boolean.TRUE) {
                throw Messages.log.cannotResolveFilteredClass(className);
            }
        }
    }

}
