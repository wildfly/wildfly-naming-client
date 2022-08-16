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

import static java.lang.Math.min;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;

import org.jboss.marshalling.ObjectResolver;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.util.MessageTracker;
import org.wildfly.naming.client.MarshallingCompatibilityHelper;
import org.wildfly.naming.client._private.Messages;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

/**
 * A class used to register a naming channel open listener.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class RemoteNamingService {
    static final int JAVAEE_PROTOCOL_VERSION = 2;
    static final int JAKARTAEE_PROTOCOL_VERSION = 3;
    // Batavia transformer sensible constant - it can start with either "javax." or "jakarta." if transformation was performed
    private static final String VARIABLE_CONSTANT = "javax.ejb.FAKE_STRING";
    static final int LATEST_VERSION = VARIABLE_CONSTANT.startsWith("jakarta") ? JAKARTAEE_PROTOCOL_VERSION : JAVAEE_PROTOCOL_VERSION;
    private static final int[] SUPPORTED_PROTOCOL_VERSIONS = LATEST_VERSION == JAKARTAEE_PROTOCOL_VERSION ? new int[] { 1, 2, 3 } : new int[] { 1, 2 };
    private final Context localContext;
    private Registration registration;
    private final Function<String, Boolean> classResolverFilter;

    public RemoteNamingService(final Context localContext) {
        this(localContext, null);
    }

    public RemoteNamingService(final Context localContext, final Function<String, Boolean> classResolverFilter) {
        this.localContext = localContext;
        this.classResolverFilter = classResolverFilter;
    }

    public void start(final Endpoint endpoint) throws IOException {
        registration = endpoint.registerService(ProtocolUtils.NAMING, new ChannelOpenListener(), OptionMap.EMPTY);
    }

    public void stop() throws IOException {
        registration.close();
    }

    private class ChannelOpenListener implements OpenListener {
        public void channelOpened(Channel channel) {
            final MessageTracker messageTracker = new MessageTracker(channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue());
            try {
                channel.receiveMessage(new Channel.Receiver() {
                    public void handleMessage(Channel channel, MessageInputStream message) {
                        try (MessageInputStream mis = message) {
                            byte[] namingHeader = new byte[6];
                            mis.read(namingHeader);
                            if (! Arrays.equals(namingHeader, ProtocolUtils.NAMING_BYTES)) {
                                throw Messages.log.invalidHeader();
                            }
                            int version = min(LATEST_VERSION, mis.readUnsignedByte());
                            boolean versionSupported = false;
                            for (int supportedProtocolVersion : SUPPORTED_PROTOCOL_VERSIONS) {
                                if (version == supportedProtocolVersion) {
                                    versionSupported = true;
                                    break;
                                }
                            }
                            if (! versionSupported) {
                                throw Messages.log.unsupportedProtocolVersion(version);
                            }
                            // Clone the context
                            Context localContext = null;
                            synchronized (RemoteNamingService.this) {
                                try {
                                    localContext = (Context) RemoteNamingService.this.getLocalContext().lookup("");
                                } catch (NamingException e) {
                                    Messages.log.unexpectedError(e);
                                }
                            }
                            final RemoteServerTransport remoteServerTransport = new RemoteServerTransport(channel, version, messageTracker, localContext, classResolverFilter);
                            final List<MarshallingCompatibilityHelper> helpers = ProtocolUtils.getMarshallingCompatibilityHelpers();
                            ObjectResolver resolver = null;
                            for (MarshallingCompatibilityHelper helper : helpers) {
                                final ObjectResolver nextResolver = helper.getObjectResolver(remoteServerTransport, false);
                                if (resolver == null) {
                                    resolver = nextResolver;
                                } else if (resolver instanceof AggregateObjectResolver) {
                                    ((AggregateObjectResolver) resolver).add(nextResolver);
                                } else {
                                    resolver = new AggregateObjectResolver().add(nextResolver);
                                }
                            }
                            if (resolver != null) remoteServerTransport.getConfiguration().setObjectResolver(resolver);
                            remoteServerTransport.start();
                        } catch (IOException | CommunicationException e) {
                            Messages.log.failedToDetermineClientVersion(e);
                        }
                    }

                    public void handleError(final Channel channel, final IOException error) {
                        try {
                            channel.close();
                        } catch (IOException ignored) {
                        }
                    }

                    public void handleEnd(final Channel channel) {
                        try {
                            channel.close();
                        } catch (IOException ignored) {
                        }
                    }
                });
                // Send greeting message
                try (MessageOutputStream mos = messageTracker.openMessage()) {
                    mos.write(ProtocolUtils.NAMING_BYTES);
                    mos.writeByte(SUPPORTED_PROTOCOL_VERSIONS.length);
                    for (int version : SUPPORTED_PROTOCOL_VERSIONS) {
                        // Old clients cannot accept a single version from the server which is greater than 1 using a signed compare; so, make it less than 1 always.
                        // New clients know about this trick and can compensate to correctly negotiate.
                        mos.writeByte(version > 1 ? version | 0x80 : version);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    IoUtils.safeClose(channel);
                }
            } catch (IOException e) {
                Messages.log.failedToSendHeader(e);
                IoUtils.safeClose(channel);
            }
        }

        public void registrationTerminated() {
        }
    }

    public Context getLocalContext() {
        return localContext;
    }
}
