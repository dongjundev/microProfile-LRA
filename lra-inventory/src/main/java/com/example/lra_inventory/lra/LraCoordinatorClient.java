package com.example.lra_inventory.lra;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import org.glassfish.jersey.client.JerseyClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LraCoordinatorClient {
    private static final Logger log = LoggerFactory.getLogger(LraCoordinatorClient.class);
    private static final String API_VERSION_HEADER = "Narayana-LRA-API-version";
    private static final String API_VERSION = "1.0";

    private final URI coordinatorUrl;
    private final Client client;

    public LraCoordinatorClient(@Value("${lra.coordinator.url}") String coordinatorUrl) {
        this.coordinatorUrl = URI.create(coordinatorUrl);
        this.client = new JerseyClientBuilder().build();
    }

    public URI startLra(String clientId, URI parentLra) {
        String parent = parentLra == null ? "" : URLEncoder.encode(parentLra.toString(), StandardCharsets.UTF_8);
        String resolvedClientId = clientId == null ? "" : clientId;

        try (Response response = client.target(coordinatorUrl)
                .path("/start")
                .queryParam("ClientID", resolvedClientId)
                .queryParam("TimeLimit", 0)
                .queryParam("ParentLRA", parent)
                .request()
                .header(API_VERSION_HEADER, API_VERSION)
                .post(null)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                log.error("LRA start failed: status={} coordinator={}", response.getStatus(), coordinatorUrl);
                throw new WebApplicationException("Failed to start LRA: " + response.getStatus(), response);
            }
            String location = response.getHeaderString("Location");
            log.info("LRA started: {}", location);
            return URI.create(location);
        }
    }

    public void closeLra(URI lraId) {
        endLra(lraId, "close");
    }

    public void cancelLra(URI lraId) {
        endLra(lraId, "cancel");
    }

    public URI joinLra(URI lraId, Map<String, URI> terminationUris, String participantData) {
        String lraUid = lraUid(lraId);
        String linkHeader = buildLinkHeader(terminationUris);
        log.info("LRA join attempt: lraId={} linkHeader={}", lraId, linkHeader);
        Object payload = participantData == null ? linkHeader : participantData;

        try (Response response = client.target(coordinatorUrl)
                .path("/" + lraUid)
                .queryParam("TimeLimit", 0)
                .request()
                .header(API_VERSION_HEADER, API_VERSION)
                .header("Link", linkHeader)
                .put(Entity.text(payload))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                String body = response.hasEntity() ? response.readEntity(String.class) : "";
                log.error("LRA join failed: lraId={} status={} body={} coordinator={}", lraId, response.getStatus(), body, coordinatorUrl);
                throw new WebApplicationException("Failed to join LRA: " + response.getStatus(), response);
            }
            String recovery = response.getHeaderString("Long-Running-Action-Recovery");
            log.info("LRA joined successfully: lraId={} recovery={}", lraId, recovery);
            return recovery != null ? URI.create(recovery) : lraId;
        }
    }

    private void endLra(URI lraId, String action) {
        String lraUid = lraUid(lraId);
        try (Response response = client.target(coordinatorUrl)
                .path("/" + lraUid + "/" + action)
                .request()
                .header(API_VERSION_HEADER, API_VERSION)
                .put(Entity.text(""))) {
            int status = response.getStatus();
            if (status != Response.Status.OK.getStatusCode()
                    && status != Response.Status.ACCEPTED.getStatusCode()
                    && status != Response.Status.NOT_FOUND.getStatusCode()) {
                log.error("LRA end failed: lraId={} action={} status={}", lraId, action, status);
                throw new WebApplicationException("Failed to end LRA: " + status, response);
            }
            log.info("LRA end: lraId={} action={} status={}", lraId, action, status);
        }
    }

    private String buildLinkHeader(Map<String, URI> terminationUris) {
        StringJoiner joiner = new StringJoiner(",");
        for (Map.Entry<String, URI> entry : terminationUris.entrySet()) {
            URI uri = entry.getValue();
            if (uri == null) {
                continue;
            }
            Link link = Link.fromUri(uri)
                    .rel(entry.getKey())
                    .title(entry.getKey())
                    .type("text/plain")
                    .build();
            joiner.add(link.toString());
        }
        return joiner.toString();
    }

    private String lraUid(URI lraId) {
        String path = lraId.getPath();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
