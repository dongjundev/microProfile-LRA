package com.example.lra_inventory.lra;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.ws.rs.container.ResourceInfo;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Component
@Provider
@Priority(Priorities.USER)
public class LraRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger log = LoggerFactory.getLogger(LraRequestFilter.class);
    private static final String LRA_ID_PROPERTY = "lra.id";
    private static final String LRA_STARTED_PROPERTY = "lra.started";
    private static final String LRA_END_PROPERTY = "lra.end";

    private final LraCoordinatorClient lraClient;
    private final URI externalBaseUri;

    @Context
    private ResourceInfo resourceInfo;

    public LraRequestFilter(LraCoordinatorClient lraClient,
                            @Value("${app.base-url}") String appBaseUrl) {
        this.lraClient = lraClient;
        this.externalBaseUri = URI.create(appBaseUrl);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        LRA lra = resolveLraAnnotation(resourceInfo);
        if (lra == null) {
            return;
        }

        URI lraId = readLraId(requestContext);
        switch (lra.value()) {
            case REQUIRES_NEW -> {
                URI parent = lraId;
                URI started = lraClient.startLra(resourceInfo.getResourceClass().getSimpleName(), parent);
                requestContext.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, started.toString());
                requestContext.setProperty(LRA_ID_PROPERTY, started);
                requestContext.setProperty(LRA_STARTED_PROPERTY, true);
                requestContext.setProperty(LRA_END_PROPERTY, lra.end());
                log.info("LRA started by filter: {}", started);
            }
            case MANDATORY -> {
                if (lraId == null) {
                    throw new WebApplicationException("Missing LRA context", Response.Status.PRECONDITION_FAILED);
                }
                requestContext.setProperty(LRA_ID_PROPERTY, lraId);
                requestContext.setProperty(LRA_END_PROPERTY, false);
                joinIfParticipant(lraId);
            }
            case REQUIRED -> {
                if (lraId == null) {
                    URI started = lraClient.startLra(resourceInfo.getResourceClass().getSimpleName(), null);
                    requestContext.getHeaders().putSingle(LRA_HTTP_CONTEXT_HEADER, started.toString());
                    requestContext.setProperty(LRA_ID_PROPERTY, started);
                    requestContext.setProperty(LRA_STARTED_PROPERTY, true);
                    requestContext.setProperty(LRA_END_PROPERTY, lra.end());
                } else {
                    requestContext.setProperty(LRA_ID_PROPERTY, lraId);
                    requestContext.setProperty(LRA_END_PROPERTY, false);
                    joinIfParticipant(lraId);
                }
            }
            default -> {
                if (lraId != null) {
                    requestContext.setProperty(LRA_ID_PROPERTY, lraId);
                }
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        boolean started = Boolean.TRUE.equals(requestContext.getProperty(LRA_STARTED_PROPERTY));
        boolean shouldEnd = Boolean.TRUE.equals(requestContext.getProperty(LRA_END_PROPERTY));
        if (!started || !shouldEnd) {
            return;
        }

        URI lraId = (URI) requestContext.getProperty(LRA_ID_PROPERTY);
        if (lraId == null) {
            return;
        }

        if (responseContext.getStatus() >= 400) {
            lraClient.cancelLra(lraId);
            log.warn("LRA cancelled by filter: {} status={}", lraId, responseContext.getStatus());
        } else {
            lraClient.closeLra(lraId);
            log.info("LRA closed by filter: {}", lraId);
        }
    }

    private void joinIfParticipant(URI lraId) {
        Path classPath = resourceInfo.getResourceClass().getAnnotation(Path.class);
        if (classPath == null) {
            return;
        }
        Map<String, URI> uris = buildTerminationUris(classPath.value());
        lraClient.joinLra(lraId, uris, null);
    }

    private Map<String, URI> buildTerminationUris(String classPath) {
        UriBuilder base = UriBuilder.fromUri(externalBaseUri).path(classPath);
        Map<String, URI> uris = new HashMap<>();
        uris.put("compensate", base.clone().path("compensate").build());
        uris.put("complete", base.clone().path("complete").build());
        uris.put("status", base.clone().path("lra-status").build());
        uris.put("forget", base.clone().path("forget").build());
        uris.put("leave", base.clone().path("leave").build());
        uris.put("after", base.clone().path("after").build());
        return uris;
    }

    private LRA resolveLraAnnotation(ResourceInfo info) {
        if (info == null) {
            return null;
        }
        Method method = info.getResourceMethod();
        if (method != null && method.isAnnotationPresent(LRA.class)) {
            return method.getAnnotation(LRA.class);
        }
        Class<?> resourceClass = info.getResourceClass();
        if (resourceClass != null && resourceClass.isAnnotationPresent(LRA.class)) {
            return resourceClass.getAnnotation(LRA.class);
        }
        return null;
    }

    private URI readLraId(ContainerRequestContext requestContext) {
        String header = requestContext.getHeaderString(LRA_HTTP_CONTEXT_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        return URI.create(header);
    }
}
