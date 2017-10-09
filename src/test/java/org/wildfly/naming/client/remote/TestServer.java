package org.wildfly.naming.client.remote;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.util.SaslFactories;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;

/**
 * @author Jason T. Greene
 */
public class TestServer {

    private String endpointName;
    private Endpoint endpoint;
    private String host;
    private int port;
    private AcceptingChannel<StreamConnection> server;

    TestServer(String endpointName, String host, int port) {
        this.endpointName = endpointName;
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception {
        // create a Remoting endpoint
        final OptionMap options = OptionMap.EMPTY;
        EndpointBuilder endpointBuilder = Endpoint.builder();
        endpointBuilder.setEndpointName(this.endpointName);
        endpointBuilder.buildXnioWorker(Xnio.getInstance()).populateFromOptions(options).build();
        this.endpoint = endpointBuilder.build();


        RemoteNamingService service = new RemoteNamingService(new FlatMockContext());
        service.start(endpoint);

        // set up a security realm called default with a user called test
        final SimpleMapBackedSecurityRealm realm = new SimpleMapBackedSecurityRealm();
        realm.setPasswordMap("test", ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, "test".toCharArray()));

        // set up a security domain which has realm "default"
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        domainBuilder.addRealm("default", realm).build();                                  // add the security realm called "default" to the security domain
        domainBuilder.setDefaultRealmName("default");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        SecurityDomain testDomain = domainBuilder.build();


        // set up a SaslAuthenticationFactory (i.e. a SaslServerFactory)
        SaslAuthenticationFactory saslAuthenticationFactory = SaslAuthenticationFactory.builder()
                .setSecurityDomain(testDomain)
                .setMechanismConfigurationSelector(mechanismInformation -> {
                    switch (mechanismInformation.getMechanismName()) {
                        case "ANONYMOUS":
                        case "PLAIN": {
                            return MechanismConfiguration.EMPTY;
                        }
                        default: return null;
                    }
                })
                .setFactory(SaslFactories.getElytronSaslServerFactory())
                .build();
        final OptionMap serverOptions = OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("ANONYMOUS"), Options.SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        final NetworkServerProvider serverProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SocketAddress bindAddress = new InetSocketAddress(InetAddress.getByName(host), port);
        this.server = serverProvider.createServer(bindAddress, serverOptions, saslAuthenticationFactory, null);


    }

    public void stop() {
        try {
            server.close();
        } catch (Throwable t)
        {
            // Yum
        }

        try {
            endpoint.close();
        } catch (Throwable t)
        {
            // Yum
        }
    }

}
