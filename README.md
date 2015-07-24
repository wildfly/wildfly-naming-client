# WildFly Naming Client

This simple JNDI/naming client library abstracts away some of the pain of JNDI by providing the following features:

* Federation support
* Class loader based provider extensibility
* A replacement implementation of the jboss-remote-naming protocol
* Abstract context implementations for supporting relative contexts and federation in custom naming providers

## Usage

To use the WildFly naming client provider, the naming client initial context has to installed.  This can be done in one of several different ways.

### System property

If you set the system property ``java.naming.factory.initial`` to ``org.wildfly.naming.client.WildFlyInitialContextFactory``, then your Java code to access an initial context is just:

```
    InitialContext ctx = new InitialContext();
    Blah blah = (Blah) ctx.lookup("foo:blah");
```

### Environment property

You can also set the ``java.naming.factory.initial`` property on the environment.  This property name is also found in the constant field ``javax.naming.Context#INITIAL_CONTEXT_FACTORY``.

```
    Hashtable<String, Object> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
    InitialContext ctx = new InitialContext(env);
    Blah blah = (Blah) ctx.lookup("foo:blah");
```

### Programmatic installation

You can re-set the default inital context factory builder using a standard naming API.

```
    NamingManager.setInitialContextFactoryBuilder(new WildFlyInitialContextFactoryBuilder());
    // later...
    InitialContext ctx = new InitialContext();
    Blah blah = (Blah) ctx.lookup("foo:blah");
```

### Direct instantiation

You can bypass the standard discovery mechanism and go directly to the ``WildFlyInitialContext``.

```
    InitialContext ctx = new WildFlyInitialContext();
    Blah blah = (Blah) ctx.lookup("foo:blah");
```

## Remote naming

In order to associate an initial context with a specific (possibly remote) naming service, a naming provider URL is required.  This is done in the standard way using the ``java.naming.provider.url`` property, found in the constant field ``javax.naming.Context#PROVIDER_URL``.

```
    Hashtable<String, Object> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "org.wildfly.naming.client.WildFlyInitialContextFactory");
    env.put(Context.PROVIDER_URL, "remote+https://nowhere.example.edu:8080");
    InitialContext ctx = new InitialContext(env);
    Blah blah = (Blah) ctx.lookup("foo:blah");
```

This property can also be specified as a system property.

Unlike the previous ``jboss-remote-naming`` project, the connection to the peer is not requested until an operation is performed on the connection, and all consumers of the same remote URL will share a connection.  The connection lifecycle is independent of any ``Context`` instances which reference it.

## Using the context

Multiple services can be looked up via the same context.  To register providers, implement the ``org.wildfly.naming.client.NamingProvider`` interface and register the implementation using the approach described in the ``java.util.ServiceLoader`` documentation.

## Maven

Find this artifact under the Maven coordinates ``org.wildfly:wildfly-naming-client``.
