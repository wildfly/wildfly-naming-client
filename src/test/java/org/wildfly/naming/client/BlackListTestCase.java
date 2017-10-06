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

import static org.wildfly.naming.client.ProviderEnvironment.TIME_MASK;

import java.net.URI;

import org.junit.Assert;
import org.junit.Test;

/**
 * Simple test to verify black-list backoff
 *
 * @author Jason T. Greene
 */
public class BlackListTestCase {

    @Test
    public void testBackOff() throws Exception {
        ProviderEnvironment.Builder builder = new ProviderEnvironment.Builder();
        ProviderEnvironment env = builder.build();
        URI foo = new URI("remote://foo");

        Assert.assertFalse(env.getBlackList().containsKey(foo));


        for (int i = 0; i < 14; i++) {
            long currentTime = System.currentTimeMillis();
            env.updateBlacklist(foo);

            Long entry = env.getBlackList().get(foo);
            Assert.assertNotNull(entry);

            int difference = (int) ((entry & TIME_MASK) - currentTime);
            int expectedLow = (1 << i) * 65536;
            int expectedHigh = ((1 << i) + 1) * 65536;

            String expected = String.format("Expected difference of iteration %d to be between %d and %d, but was %d [*] delta = %d | %d (mul %d) - [*] %s", i, expectedLow, expectedHigh, difference, difference - expectedLow, difference - expectedHigh, entry & ~TIME_MASK, formatDuration(difference));
            Assert.assertTrue(expected, difference >= expectedLow && difference <= expectedHigh);
            Assert.assertEquals(1 << (i + 1), entry & ~TIME_MASK);
        }

        // Verify backoff progression stops at 6 days
        for (int i = 15; i < 30; i++) {
            long currentTime = System.currentTimeMillis();
            env.updateBlacklist(foo);

            Long entry = env.getBlackList().get(foo);
            Assert.assertNotNull(entry);

            int difference = (int) ((entry & TIME_MASK) - currentTime);
            int expectedLow = (1 << 13) * 65536;
            int expectedHigh = ((1 << 13) + 1) * 65536;

            String expected = String.format("Expected difference of iteration %d to be between %d and %d, but was %d [*] delta = %d | %d (mul %d) - [*] %s", i, expectedLow, expectedHigh, difference, difference - expectedLow, difference - expectedHigh, entry & ~TIME_MASK, formatDuration(difference));
            Assert.assertTrue(expected, difference >= expectedLow && difference <= expectedHigh);
            Assert.assertEquals(1 << (13 + 1), entry & ~TIME_MASK);
        }
    }

    private static String formatDuration(long duration) {
        int days = (int)(duration / 86400_000L);
        duration -= days * 86400_000L;
        int hours = (int) (duration / 3600_000L);
        duration -= hours * 3600_000L;
        int mins = (int) (duration / 60_000);
        duration -= mins * 60_000;
        int secs = (int) (duration / 1000);
        duration -= secs * 1000;
        int millis = (int) duration;

        return ((days > 0) ? String.format("%d days,", days) : "") +
                ((hours > 0) ? String.format("%d hours,", hours) : "") +
                ((mins > 0) ? String.format("%d mins,", mins) : "") +
                ((secs > 0) ? String.format("%d secs,", secs) : "") +
                ((millis > 0) ? String.format("%d mils", millis) : "");
    }
}
