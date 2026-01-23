package com.example.lra_payment.config;

import com.example.lra_payment.lra.LraRequestFilter;
import com.example.lra_payment.resource.PaymentResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JerseyConfig extends ResourceConfig {
    public JerseyConfig() {
        register(PaymentResource.class);
        register(LraRequestFilter.class);
    }
}
