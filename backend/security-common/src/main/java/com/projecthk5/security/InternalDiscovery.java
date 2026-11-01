package com.projecthk5.security;

import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.ServiceRequestWrapper;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Sends internal calls to the Eureka instance named by the URL host. A host Eureka does not know
 * keeps its normal DNS route, so a service still answers while Eureka is down.
 * Static because internal clients are built in constructors, before any bean can be injected.
 */
@AutoConfiguration
public class InternalDiscovery {
    private static volatile LoadBalancerClient balancer;

    InternalDiscovery(ObjectProvider<LoadBalancerClient> client) {
        balancer = client.getIfAvailable();
    }

    static ClientHttpResponse route(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        LoadBalancerClient current = balancer;
        ServiceInstance instance = current == null ? null : current.choose(request.getURI().getHost());
        return execution.execute(instance == null ? request : new ServiceRequestWrapper(request, instance, current), body);
    }
}
