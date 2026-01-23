package com.example.lra_order.config;

import com.example.lra_order.lra.LraRequestFilter;
import com.example.lra_order.resource.OrderResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JerseyConfig extends ResourceConfig {
    public JerseyConfig() {
        register(OrderResource.class);
        register(LraRequestFilter.class);
    }
}
