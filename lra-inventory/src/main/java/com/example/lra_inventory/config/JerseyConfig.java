package com.example.lra_inventory.config;

import com.example.lra_inventory.lra.LraRequestFilter;
import com.example.lra_inventory.resource.InventoryResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JerseyConfig extends ResourceConfig {
    public JerseyConfig() {
        register(InventoryResource.class);
        register(LraRequestFilter.class);
    }
}
