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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A context with an invocation life-span that maintains state useful to
 * retrying naming operations.
 *
 * @author Jason T. Greene
 */
@SuppressWarnings("WeakerAccess")
public final class RetryContext {

    static int MAX_RECORDED_FAILURES = 10;

    private URI destination;
    private Set<URI> transientFailed = Collections.emptySet();
    private List<Throwable> failures = Collections.emptyList();
    private boolean explicitFailure = false;

    /**
     * Sets the current destination being attempted by the invocation.
     *
     * @param destination the current destination
     */
    public void setCurrentDestination(URI destination) {
        this.destination = destination;
    }

    /**
     * Gets the current destination being attempted by the invocation.
     *
     * @return the current destination
     */
    public URI currentDestination() {
        return destination;
    }

    /**
     * Indicates whether the specified destination has been registered as
     * failing as part of this invocation. Note that this will only return true
     * for destinations that have transiently failed. If there is a blocklist
     * entry for a destination, then it is not recorded as a transient failure.
     *
     * @param destination the destination to check
     * @return true if a transient failure is registered
     */
    public boolean hasTransientlyFailed(URI destination) {
        return transientFailed.contains(destination);
    }

    /**
     * Registers a destination as having transiently failed. Destinations
     * that have transiently failed should not be retried in the
     * corresponding invocation of this context. However, future invocations
     * should retry, unlike a blocklisted destination, which instead utilizes
     * time-based back-off.
     *
     * @param destination the destination to record a transient failure
     */
    public void addTransientFail(URI destination) {
        if (transientFailed.size() == 0) {
            transientFailed = new HashSet<>(1);
        }
        transientFailed.add(destination);
    }

    /**
     * Return the current number of transient failures that have been registered
     * against this context.
     *
     * @return the number of transient failures registered
     */
    public int transientFailCount() {
        return transientFailed.size();
    }

    /**
     * Gets a list of exceptions for failures that have occurred while retrying
     * this invocation. Note that this list may include both blocklisted and
     * transient failures, and is not necessarily exhaustive. This list is
     * purely intended for informational error-reporting. Callers should not
     * make assumptions based on the content of it.
     *
     * @return a list of throwables thrown while attempting contact with a
     *         destination
     */
    public List<Throwable> getFailures() {
        return failures;
    }

    /**
     * Indicates to a naming provider that this exception should be thrown, as
     * opposed to a "no more destinations" summary exception, if no other
     * destinations are successful during the invocation.
     *
     * @param failure the Throwable that should be thrown if no other
     *                destinations succeed.
     */
    public void addExplicitFailure(Throwable failure) {
        explicitFailure = true;
        failures = Collections.singletonList(failure);
    }

    /**
     * Returns true if an explicit failure has been registered. This occurs as
     * the result of a call to {@link #addExplicitFailure(Throwable)}.
     *
     * @return true if an explicit failure has been registered.
     */
    public boolean hasExplicitFailure() {
        return explicitFailure;
    }


    /**
     * Register an exception that was observed while attempting to execute an
     * operation against a destination.
     *
     * @param failure the throwable thrown during a destination attempt
     */
    public void addFailure(Throwable failure) {
        if (explicitFailure || failures.size() >= MAX_RECORDED_FAILURES) {
            return;
        }

        if (failures.size() == 0) {
            failures = new ArrayList<>(1);
        }

        // Store only one exception per "type" of failure
        failures.add(failure);
    }
}