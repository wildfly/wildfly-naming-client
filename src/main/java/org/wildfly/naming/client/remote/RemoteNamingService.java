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
import java.util.Arrays;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.util.MessageTracker;
import org.wildfly.naming.client._private.Messages;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

/**
 * A class used to register a naming channel open listener.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class RemoteNamingService {
    private static byte[] SUPPORTED_PROTOCOL_VERSIONS = new byte[] { 1, 2 };
    private final Context localContext;
    private Registration registration;

    public RemoteNamingService(final Context localContext) {
        this.localContext = localContext;
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
                            int version = mis.readUnsignedByte();
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
                            final RemoteServerTransport remoteServerTransport = new RemoteServerTransport(channel, version, messageTracker, localContext);
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
                    for (byte version : SUPPORTED_PROTOCOL_VERSIONS) {
                        mos.writeByte(version);
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
