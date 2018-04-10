/*
Copyright 2018 Red Hat, Inc.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.wildfly.naming.client;

import org.junit.Assert;
import org.junit.Test;

import javax.naming.NamingException;
import javax.naming.NoInitialContextException;


/**
 * @author <a href="mailto:padamec@redhat.com">Petr Adamec</a>
 */
public class WildFlyInitialContextTestCase {
    /**
     * Test if exception NoInitialContextException is not thrown if client use WildFlyInitialContext without properties</br>
     * For more information visit https://issues.jboss.org/browse/JBEAP-13936
     * @throws NamingException
     */
    @Test
    public void testCreateInitialContextWithoutProperties() throws NamingException {
        try{

            WildFlyInitialContext ic = new WildFlyInitialContext();
            ic.getDefaultInitCtx();
        }catch(NoInitialContextException e){
            Assert.fail("Test failed due to catching NoInitialContextException");
        }
    }
}
