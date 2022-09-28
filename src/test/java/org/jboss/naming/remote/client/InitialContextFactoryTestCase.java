package org.jboss.naming.remote.client;

import org.junit.Assert;
import org.junit.Test;

import javax.naming.Context;
import javax.naming.NamingException;
import java.util.Hashtable;


@SuppressWarnings("UnnecessaryBoxing")
public class InitialContextFactoryTestCase {

    @Test
    public void testGetInitialContext() throws NamingException {
        Hashtable<Object, Object> inputEnv = new Hashtable<>();
        inputEnv.put("string.prop", "1");
        inputEnv.put("integer.prop", Integer.valueOf(2));
        inputEnv.put("long.prop", Long.valueOf(3));

        InitialContextFactory factory = new InitialContextFactory();
        Context initialContext = factory.getInitialContext(inputEnv);
        Hashtable<?, ?> contextEnv = initialContext.getEnvironment();

        Assert.assertEquals("1", contextEnv.get("string.prop"));
        Assert.assertEquals("2", contextEnv.get("integer.prop"));
        Assert.assertEquals("3", contextEnv.get("long.prop"));
    }
}
