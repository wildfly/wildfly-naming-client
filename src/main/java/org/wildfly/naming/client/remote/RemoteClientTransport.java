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

import static org.wildfly.naming.client.remote.ProtocolUtils.createMarshaller;
import static org.wildfly.naming.client.remote.ProtocolUtils.createUnmarshaller;
import static org.wildfly.naming.client.remote.ProtocolUtils.readId;
import static org.wildfly.naming.client.remote.ProtocolUtils.writeId;
import static org.wildfly.naming.client.remote.TCCLUtils.getAndSetSafeTCCL;
import static org.wildfly.naming.client.remote.TCCLUtils.resetTCCL;
import static org.wildfly.naming.client.util.NamingUtils.namingException;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import javax.naming.Binding;
import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NamingException;

import org.jboss.marshalling.ContextClassResolver;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.ClientServiceHandle;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.ConnectionPeerIdentity;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.util.BlockingInvocation;
import org.jboss.remoting3.util.InvocationTracker;
import org.wildfly.naming.client.CloseableNamingEnumeration;
import org.wildfly.naming.client.MarshallingCompatibilityHelper;
import org.wildfly.naming.client._private.Messages;
import org.wildfly.naming.client.store.RelativeFederatingContext;
import org.wildfly.naming.client.util.FastHashtable;
import org.wildfly.naming.client.util.NamingUtils;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;

/**
 * The client side of the remote naming transport protocol.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class RemoteClientTransport implements RemoteTransport {
    static final ClientServiceHandle<RemoteClientTransport> SERVICE_HANDLE = new ClientServiceHandle<>(ProtocolUtils.NAMING, RemoteClientTransport::construct);

    private static final byte[] initialBytes = {
        'n', 'a', 'm', 'i', 'n', 'g'
    };

    private final MarshallingConfiguration configuration;
    private final InvocationTracker tracker;
    private final Channel channel;
    private final int version;

    RemoteClientTransport(final Channel channel, final int version, final MarshallingConfiguration configuration) {
        configuration.setClassResolver(new ContextClassResolver());
        this.channel = channel;
        this.configuration = configuration;
        this.version = version;
        tracker = new InvocationTracker(channel, version == 1 ? IntUnaryOperator.identity() : RemoteClientTransport::defaultFunction);
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

    private static int defaultFunction(int random) {
        return random & 0xffff;
    }

    private static IoFuture<RemoteClientTransport> construct(final Channel channel) {
        FutureResult<RemoteClientTransport> futureResult = new FutureResult<>(channel.getConnection().getEndpoint().getXnioWorker());
        channel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                final ClassLoader oldCL = getAndSetSafeTCCL();
                try {
                    futureResult.setException(error);
                } finally {
                    resetTCCL(oldCL);
                }
            }

            public void handleEnd(final Channel channel) {
                final ClassLoader oldCL = getAndSetSafeTCCL();
                try {
                    futureResult.setCancelled();
                } finally {
                    resetTCCL(oldCL);
                }
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                final ClassLoader oldCL = getAndSetSafeTCCL();
                // this should be the greeting message, get the version list and start from there
                try (MessageInputStream mis = message) {
                    final byte[] namingHeader = new byte[ProtocolUtils.NAMING_BYTES.length];
                    mis.read(namingHeader);
                    if (! Arrays.equals(namingHeader, ProtocolUtils.NAMING_BYTES)) {
                        futureResult.setException(new IOException(Messages.log.invalidHeader()));
                        return;
                    }
                    int length = mis.readUnsignedByte();
                    boolean hasOne = false, hasTwo = false;
                    for (int i = 0; i < length; i ++) {
                        // Servers present versions >= 2 with the MSB set so that old clients don't get confused.
                        // We strip the MSB to compensate.
                        int v = mis.readUnsignedByte() & 0x7f;
                        if (v == 1) {
                            hasOne = true;
                        } else if (v == 2) {
                            hasTwo = true;
                        }
                    }
                    int version;
                    if (hasTwo) {
                        version = 2;
                    } else if (hasOne) {
                        version = 1;
                    } else {
                        futureResult.setException(new IOException(Messages.log.noCompatibleVersions()));
                        return;
                    }
                    final MarshallingConfiguration configuration = new MarshallingConfiguration();
                    configuration.setVersion(version == 2 ? 4 : 2);
                    RemoteClientTransport remoteClientTransport = new RemoteClientTransport(channel, version, configuration);
                    final List<MarshallingCompatibilityHelper> helpers = ProtocolUtils.getMarshallingCompatibilityHelpers();
                    ObjectResolver resolver = null;
                    for (MarshallingCompatibilityHelper helper : helpers) {
                        final ObjectResolver nextResolver = helper.getObjectResolver(remoteClientTransport, true);
                        if (resolver == null) {
                            resolver = nextResolver;
                        } else if (resolver instanceof AggregateObjectResolver) {
                            ((AggregateObjectResolver) resolver).add(nextResolver);
                        } else {
                            resolver = new AggregateObjectResolver().add(nextResolver);
                        }
                    }
                    if (resolver != null) remoteClientTransport.getConfiguration().setObjectResolver(resolver);
                    try (MessageOutputStream os = remoteClientTransport.tracker.allocateMessage()) {
                        os.write(ProtocolUtils.NAMING_BYTES);
                        os.writeByte(version);
                    }
                    remoteClientTransport.start();
                    futureResult.setResult(remoteClientTransport);
                } catch (IOException e) {
                    safeClose(channel);
                    futureResult.setException(new IOException(Messages.log.connectFailed(e)));
                } finally {
                    resetTCCL(oldCL);
                }
            }
        });
        futureResult.addCancelHandler(new Cancellable() {
            public Cancellable cancel() {
                if (futureResult.setCancelled()) {
                    safeClose(channel);
                }
                return this;
            }
        });
        return futureResult.getIoFuture();
    }

    void start() {
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
                try {
                    final int messageId = message.readUnsignedByte();
                    final int id = readId(message, version);
                    final int result = message.readUnsignedByte();
                    if (result == Protocol.SUCCESS || result == Protocol.FAILURE) {
                        tracker.signalResponse(id, messageId, message, true);
                    } else {
                        throw Messages.log.outcomeNotUnderstood();
                    }
                    channel.receiveMessage(this);
                } catch (IOException e) {
                    safeClose(channel);
                    safeClose(message);
                } finally {
                    resetTCCL(oldCL);
                }
            }
        });
    }

    Object lookup(final RemoteContext context, final Name name, final ConnectionPeerIdentity peerIdentity, final boolean preserveLinks) throws NamingException {
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                // lookup
                messageOutputStream.writeByte(preserveLinks ? Protocol.CMD_LOOKUP_LINK : Protocol.CMD_LOOKUP);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(name));
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(NamingUtils.toCompositeName(name).toString());
                }
            }
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                final int parameterType = is.readUnsignedByte();
                if (parameterType == Protocol.P_CONTEXT) {
                    return new RelativeFederatingContext(new FastHashtable<>(context.getEnvironment()), context, NamingUtils.toCompositeName(name));
                } else if (parameterType == Protocol.P_OBJECT) {
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        return unmarshaller.readObject();
                    }
                } else if (parameterType == Protocol.P_EXCEPTION) {
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        final Exception exception = unmarshaller.readObject(Exception.class);
                        if (exception instanceof NamingException) {
                            throw (NamingException) exception;
                        } else {
                            throw namingException("Failed to lookup", exception);
                        }
                    }
                } else {
                    throw Messages.log.invalidResponse();
                }
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to lookup", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }

    void bind(final Name name, final Object obj, final ConnectionPeerIdentity peerIdentity, final boolean rebind) throws NamingException {
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                // bind
                messageOutputStream.writeByte(rebind ? Protocol.CMD_REBIND : Protocol.CMD_BIND);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(name));
                        marshaller.writeByte(Protocol.P_OBJECT);
                        marshaller.writeObject(obj);
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(NamingUtils.toCompositeName(name).toString());
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeObject(obj);
                    }
                }
            }
            // check responses
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                final int parameterType = is.readUnsignedByte();
                if (parameterType == Protocol.P_EXCEPTION) {
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        final Exception exception = unmarshaller.readObject(Exception.class);
                        if (exception instanceof NamingException) {
                            throw (NamingException) exception;
                        } else {
                            throw namingException("Failed to bind", exception);
                        }
                    }
                } else {
                    throw Messages.log.invalidResponse();
                }
            } catch (IOException ioe) {
                // no content on success.
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to bind", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }

    void unbind(final Name name, final ConnectionPeerIdentity peerIdentity) throws NamingException {
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                // bind
                messageOutputStream.writeByte(Protocol.CMD_UNBIND);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(name));
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(NamingUtils.toCompositeName(name).toString());
                }
            }
            // check responses
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                final int parameterType = is.readUnsignedByte();
                if (parameterType == Protocol.P_EXCEPTION) {
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        final Exception exception = unmarshaller.readObject(Exception.class);
                        if (exception instanceof NamingException) {
                            throw (NamingException) exception;
                        } else {
                            throw namingException("Failed to unbind", exception);
                        }
                    }
                } else {
                    throw Messages.log.invalidResponse();
                }
            } catch (IOException ioe) {
                // no content on success.
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to unbind", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }

    void rename(final Name oldName, final Name newName, final ConnectionPeerIdentity peerIdentity) throws NamingException {
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                messageOutputStream.writeByte(Protocol.CMD_RENAME);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(oldName));
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(newName));
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(NamingUtils.toCompositeName(oldName).toString());
                    messageOutputStream.writeUTF(NamingUtils.toCompositeName(newName).toString());
                }
            }
            // check responses
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                final int parameterType = is.readUnsignedByte();
                if (parameterType == Protocol.P_EXCEPTION) {
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        final Exception exception = unmarshaller.readObject(Exception.class);
                        if (exception instanceof NamingException) {
                            throw (NamingException) exception;
                        } else {
                            throw namingException("Failed to rename", exception);
                        }
                    }
                } else {
                    throw Messages.log.invalidResponse();
                }
            } catch (IOException ioe) {
                // no content on success.
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to rename", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }

    void destroySubcontext(final Name name, final ConnectionPeerIdentity peerIdentity) throws NamingException {
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                // bind
                messageOutputStream.writeByte(Protocol.CMD_DESTROY_SUBCTX);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(name));
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(NamingUtils.toCompositeName(name).toString());
                }
            }
            // check responses
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                final int parameterType = is.readUnsignedByte();
                if (parameterType == Protocol.P_EXCEPTION) {
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        final Exception exception = unmarshaller.readObject(Exception.class);
                        if (exception instanceof NamingException) {
                            throw (NamingException) exception;
                        } else {
                            throw namingException("Failed to destroySubcontext", exception);
                        }
                    }
                } else {
                    throw Messages.log.invalidResponse();
                }
            } catch (IOException ioe) {
                // no content on success.
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to destroySubcontext", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }

    void createSubcontext(final CompositeName compositeName, final ConnectionPeerIdentity peerIdentity) throws NamingException {
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                // bind
                messageOutputStream.writeByte(Protocol.CMD_CREATE_SUBCTX);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(compositeName);
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(compositeName.toString());
                }
            }
            // check responses
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                final int parameterType = is.readUnsignedByte();
                if (parameterType == Protocol.P_EXCEPTION) {
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        final Exception exception = unmarshaller.readObject(Exception.class);
                        if (exception instanceof NamingException) {
                            throw (NamingException) exception;
                        } else {
                            throw namingException("Failed to createSubcontext", exception);
                        }
                    }
                } else {
                    throw Messages.log.invalidResponse();
                }
            } catch (IOException ioe) {
                // no content on success.
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to createSubcontext", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }

    CloseableNamingEnumeration<NameClassPair> list(final Name name, final ConnectionPeerIdentity peerIdentity) throws NamingException {
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final CompositeName compositeName = NamingUtils.toCompositeName(name);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                // bind
                messageOutputStream.writeByte(Protocol.CMD_LIST);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(name));
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(compositeName.toString());
                }
            }
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                if (version == 1) {
                    if (is.readUnsignedByte() != Protocol.P_LIST) {
                        throw Messages.log.invalidResponse();
                    }
                    final int listSize = is.readInt();
                    final List<NameClassPair> results = new ArrayList<>(listSize);
                    try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                        for (int i = 0; i < listSize; i++) {
                            results.add(unmarshaller.readObject(NameClassPair.class));
                        }
                    }
                    return CloseableNamingEnumeration.fromIterable(results);
                } else {
                    final int listSize = is.readInt();
                    final List<NameClassPair> results = new ArrayList<>(listSize);
                    for (int i = 0; i < listSize; i ++) {
                        String itemName = is.readUTF();
                        String itemClass = is.readUTF();
                        final NameClassPair nameClassPair = new NameClassPair(itemName, itemClass, true);
                        final CompositeName inNamespace = (CompositeName) compositeName.clone();
                        inNamespace.add(itemName);
                        nameClassPair.setNameInNamespace(inNamespace.toString());
                        results.add(nameClassPair);
                    }
                    return CloseableNamingEnumeration.fromIterable(results);
                }
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to lookup", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }

    CloseableNamingEnumeration<Binding> listBindings(final Name name, final RemoteContext remoteContext, final ConnectionPeerIdentity peerIdentity) throws NamingException {
        final int authId = peerIdentity.getId();
        final int version = this.version;
        if (version == 1 && authId != 0) {
            throw Messages.log.connectionSharingUnsupported();
        }
        final BlockingInvocation invocation = tracker.addInvocation(BlockingInvocation::new);
        final CompositeName compositeName = NamingUtils.toCompositeName(name);
        try {
            try (MessageOutputStream messageOutputStream = tracker.allocateMessage(invocation)) {
                // bind
                messageOutputStream.writeByte(Protocol.CMD_LIST_BINDINGS);
                writeId(messageOutputStream, version, invocation.getIndex());
                if (version == 1) {
                    try (Marshaller marshaller = createMarshaller(messageOutputStream, configuration)) {
                        marshaller.writeByte(Protocol.P_NAME);
                        marshaller.writeObject(NamingUtils.toDecomposedCompositeName(name));
                    }
                } else {
                    messageOutputStream.writeInt(authId);
                    messageOutputStream.writeUTF(compositeName.toString());
                }
            }
            final BlockingInvocation.Response response = invocation.getResponse();
            try (MessageInputStream is = response.getInputStream()) {
                if (version == 1 && is.readUnsignedByte() != Protocol.P_LIST) {
                    throw Messages.log.invalidResponse();
                }
                final int listSize = is.readInt();
                final List<Binding> results = new ArrayList<>(listSize);
                try (Unmarshaller unmarshaller = createUnmarshaller(is, configuration)) {
                    for (int i = 0; i < listSize; i++) {
                        final int b = unmarshaller.readUnsignedByte();
                        if (b == Protocol.P_CONTEXT) {
                            CompositeName prefix = (CompositeName) compositeName.clone();
                            final String relName = unmarshaller.readUTF();
                            prefix.add(relName);
                            final RelativeFederatingContext context = new RelativeFederatingContext(new FastHashtable<String, Object>(remoteContext.getEnvironment()), remoteContext, prefix);
                            results.add(new Binding(relName, context, true));
                        } else if (b == Protocol.P_BINDING) {
                            results.add(unmarshaller.readObject(Binding.class));
                        } else {
                            throw Messages.log.invalidResponse();
                        }
                    }
                }
                return CloseableNamingEnumeration.fromIterable(results);
            }
        } catch (ClassNotFoundException e) {
            throw namingException("Failed to lookup", e);
        } catch (IOException e) {
            throw Messages.log.operationFailed(e);
        } catch (InterruptedException e) {
            invocation.cancel();
            Thread.currentThread().interrupt();
            throw Messages.log.operationInterrupted();
        }
    }
}
