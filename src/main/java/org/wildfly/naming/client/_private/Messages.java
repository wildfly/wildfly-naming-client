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

package org.wildfly.naming.client._private;

import java.io.IOException;
import java.util.ServiceConfigurationError;

import javax.naming.CommunicationException;
import javax.naming.ConfigurationException;
import javax.naming.Context;
import javax.naming.InterruptedNamingException;
import javax.naming.InvalidNameException;
import javax.naming.Name;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Property;
import org.wildfly.naming.client.RenameAcrossNamingProvidersException;

@MessageLogger(projectCode = "WFNAM", length = 5)
public interface Messages extends BasicLogger {
    Messages log = Logger.getMessageLogger(Messages.class, "org.wildfly.naming");

    // ===== TRACE =====

    @LogMessage(level = Logger.Level.TRACE)
    @Message(value = "Service configuration failure loading naming providers")
    void serviceConfigFailed(@Cause ServiceConfigurationError error);

    // ===== normal messages =====

    @LogMessage
    @Message(id = 0, value = "WildFly Naming version %s")
    void greeting(String version);

    @Message(id = 1, value = "Failed to create object from reference")
    NamingException objectFromReference(@Cause Throwable cause);

    @Message(id = 2, value = "Failed to dereference link")
    NamingException dereferenceLink(@Cause Throwable t);

    @Message(id = 3, value = "Invalid naming provider URI: %s")
    ConfigurationException invalidProviderUri(@Cause Exception cause, Object providerUri);

    @Message(id = 4, value = "Name \"%s\" is not found")
    NameNotFoundException nameNotFound(Name name1, @Property(name = "resolvedName") Name name);

    @Message(id = 5, value = "Invalid empty name")
    InvalidNameException invalidEmptyName();

    @Message(id = 6, value = "Cannot modify read-only naming context")
    NoPermissionException readOnlyContext();

    @Message(id = 7, value = "Invalid URL scheme name \"%s\"")
    InvalidNameException invalidURLSchemeName(String name);

    @Message(id = 8, value = "Invalid relative name \"%s\"")
    InvalidNameException invalidRelativeName(String relativeName);

    @Message(id = 9, value = "Name index %d is out of bounds")
    IndexOutOfBoundsException nameIndexOutOfBounds(int pos);

    @Message(id = 10, value = "Invalid null name segment at index %d")
    IllegalArgumentException invalidNullSegment(int index);

    @Message(id = 11, value = "Missing close quote '%s' in name \"%s\"")
    InvalidNameException missingCloseQuote(char quote, String name);

    @Message(id = 12, value = "Unterminated escape sequence in name \"%s\"")
    InvalidNameException missingEscape(String name);

    @Message(id = 13, value = "Name URL scheme \"%s\" is not valid")
    InvalidNameException invalidNameUrlScheme(String urlScheme);

    @Message(id = 14, value = "Renaming from \"%s\" to \"%s\" across providers is not supported")
    RenameAcrossNamingProvidersException renameAcrossProviders(Name oldName, Name newName);

    @Message(id = 15, value = "Composite name segment \"%s\" does not refer to a context")
    NotContextException notContextInCompositeName(String segment);

    @LogMessage(level = Logger.Level.DEBUG)
    @Message(id = 16, value = "Closing context \"%s\" failed")
    void contextCloseFailed(Context context, @Cause Throwable cause);

    @Message(id = 17, value = "No JBoss Remoting endpoint has been configured")
    CommunicationException noRemotingEndpoint();

    @Message(id = 18, value = "Failed to connect to remote host")
    CommunicationException connectFailed(@Cause Throwable cause);

    @Message(id = 19, value = "Naming operation interrupted")
    InterruptedNamingException operationInterrupted();

    @Message(id = 20, value = "Remote naming operation failed")
    CommunicationException operationFailed(@Cause Throwable cause);

    @Message(id = 21, value = "Connection terminated")
    CommunicationException connectionEnded();

    @Message(id = 22, value = "The server provided no compatible protocol versions")
    CommunicationException noCompatibleVersions();

    @Message(id = 23, value = "Received an invalid response from the server")
    CommunicationException invalidResponse();

    @Message(id = 24, value = "Naming operation not supported")
    OperationNotSupportedException notSupported();

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 25, value = "org.jboss.naming.remote.client.InitialContextFactory is deprecated; new applications should use org.wildfly.naming.client.WildFlyInitialContextFactory instead")
    void oldContextDeprecated();

    @Message(id = 26, value = "No provider for found for URI: %s")
    OperationNotSupportedException noProviderForUri(String uri);

    @Message(id = 27, value = "Invalid naming permission action \"%s\"")
    IllegalArgumentException invalidPermissionAction(String action);

    @Message(id = 28, value = "Naming provider instance close failed")
    CommunicationException namingProviderCloseFailed(@Cause Throwable cause);

    @Message(id = 29, value = "Invalid leading bytes in header")
    CommunicationException invalidHeader();

    @Message(id = 30, value = "Unexpected response parameter received")
    IOException unexpectedResponseParameter();

    @Message(id = 31, value = "Outcome not understood")
    IOException outcomeNotUnderstood();
}
