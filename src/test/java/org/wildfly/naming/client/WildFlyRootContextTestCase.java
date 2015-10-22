package org.wildfly.naming.client;

import javax.naming.CompositeName;
import javax.naming.NamingException;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.naming.client.util.FastHashtable;

/**
 *  @author <a href="mailto:tadamski@redhat.com">Tomasz Adamski</a>
 */

public class WildFlyRootContextTestCase {

    @Test
     public void testReparsing() throws NamingException{
        testReparseName("", null, "");
        testReparseName("/", null, "/");
        testReparseName("foo/", null, "foo/");
        testReparseName("/foo", null, "/foo");
        testReparseName("foo/bar", null, "foo/bar");
        testReparseName("//", null, "//");

        testReparseName("ejb:", "ejb", "");
        testReparseName("ejb:/", "ejb", "/");
        testReparseName("ejb:foo/", "ejb", "foo/");
        testReparseName("ejb:foo", "ejb", "foo");
        testReparseName("ejb:/foo", "ejb", "/foo");
        testReparseName("ejb:foo/bar", "ejb", "foo/bar");
        testReparseName("ejb://", "ejb", "//");
    }

    private void testReparseName(final String origName, final String expectedUrlScheme, final String expectedName) throws NamingException{
        final CompositeName comName = new CompositeName(origName);
        final WildFlyRootContext context = new WildFlyRootContext(new FastHashtable<>());
        final WildFlyRootContext.ReparsedName reparsedName = context.reparse(comName);
        Assert.assertEquals(reparsedName.getUrlScheme(), expectedUrlScheme);
        Assert.assertEquals(reparsedName.getName(),new CompositeName(expectedName));
    }

}
