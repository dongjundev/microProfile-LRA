package com.example.lra_inventory.resource;

import com.example.lra_inventory.dto.InventoryRequest;
import com.example.lra_inventory.dto.InventoryResponse;
import com.example.lra_inventory.entity.InventoryReservation;
import com.example.lra_inventory.repository.InventoryReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Component
@Path("/inventory")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InventoryResource {
    private static final Logger log = LoggerFactory.getLogger(InventoryResource.class);
    private final InventoryReservationRepository repository;
    private final ObjectMapper objectMapper;

    public InventoryResource(InventoryReservationRepository repository,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @POST
    @Path("/reserve")
    @LRA(value = LRA.Type.MANDATORY, end = false)
    public InventoryResponse reserve(InventoryRequest request,
                                     @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        if (lraId == null) {
            throw new WebApplicationException("Missing LRA context", Response.Status.PRECONDITION_FAILED);
        }

        // Narayana automatically joins this participant to the LRA
        log.info("Inventory processing: orderId={} lraId={}", request.orderId(), lraId);

        String requestJson = toJson(request);

        // Determine status based on failure flag
        String status = request.fail() ? "FAILED" : "TRY";

        InventoryReservation reservation = new InventoryReservation(
                request.orderId(),
                lraId.toString(),
                status,
                requestJson
        );
        repository.save(reservation);

        // Log failure but still return 200 OK so LRA participant is properly registered
        // The Order service will check the response status field to detect failure
        if (request.fail()) {
            log.info("Inventory reservation failed (simulated): orderId={} lraId={}", request.orderId(), lraId);
        }

        return new InventoryResponse(request.orderId(), reservation.getStatus(), reservation.getLraId());
    }

    @PUT
    @Path("/complete")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Complete
    public Response complete(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        log.info("Inventory complete callback: lraId={}", lraId);
        try {
            InventoryReservation reservation = findByLra(lraId);
            reservation.setStatus("COMPLETED");
            repository.save(reservation);
            log.info("Inventory completed successfully: orderId={} lraId={}", reservation.getOrderId(), lraId);
            return Response.ok(ParticipantStatus.Completed.name()).build();
        } catch (Exception e) {
            log.error("Inventory complete failed: lraId={}", lraId, e);
            return Response.ok(ParticipantStatus.Completed.name()).build();
        }
    }

    @PUT
    @Path("/compensate")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Compensate
    public Response compensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        log.info("Inventory compensate callback: lraId={}", lraId);
        try {
            InventoryReservation reservation = findByLra(lraId);
            reservation.setStatus("COMPENSATED");
            repository.save(reservation);
            log.info("Inventory compensated successfully: orderId={} lraId={}", reservation.getOrderId(), lraId);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        } catch (Exception e) {
            log.error("Inventory compensate failed: lraId={}", lraId, e);
            return Response.ok(ParticipantStatus.Compensated.name()).build();
        }
    }

    @PUT
    @Path("/forget")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response forget(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        return Response.ok().build();
    }

    @PUT
    @Path("/leave")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response leave(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        return Response.ok().build();
    }

    @PUT
    @Path("/after")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response after(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        return Response.ok().build();
    }

    @GET
    @Path("/status/{orderId}")
    public InventoryResponse status(@PathParam("orderId") String orderId) {
        InventoryReservation reservation = repository.findTopByOrderId(orderId)
                .orElseThrow(() -> new WebApplicationException("Reservation not found", Response.Status.NOT_FOUND));
        return new InventoryResponse(reservation.getOrderId(), reservation.getStatus(), reservation.getLraId());
    }

    @GET
    @Path("/lra-status")
    @Produces(MediaType.TEXT_PLAIN)
    @Status
    public Response lraStatus(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                              @Context HttpHeaders headers) {
        if (lraId == null) {
            log.error("Inventory lra-status missing LRA header. headers={}", headers.getRequestHeaders());
            return Response.ok(ParticipantStatus.Active.name()).build();
        }
        log.info("Inventory lra-status callback: lraId={}", lraId);
        try {
            InventoryReservation reservation = findByLra(lraId);
            String status = switch (reservation.getStatus()) {
                case "COMPLETED" -> ParticipantStatus.Completed.name();
                case "COMPENSATED" -> ParticipantStatus.Compensated.name();
                // FAILED means business logic failed, but compensate hasn't been called yet
                // So we return Active to signal Narayana to call compensate
                case "FAILED", "TRY" -> ParticipantStatus.Active.name();
                default -> ParticipantStatus.Active.name();
            };
            log.info("Inventory lra-status: orderId={} internalStatus={} lraStatus={}",
                    reservation.getOrderId(), reservation.getStatus(), status);
            return Response.ok(status).build();
        } catch (Exception e) {
            log.error("Inventory lra-status failed: lraId={}", lraId, e);
            return Response.ok(ParticipantStatus.Active.name()).build();
        }
    }

    private InventoryReservation findByLra(URI lraId) {
        return repository.findTopByLraId(lraId.toString())
                .orElseThrow(() -> new WebApplicationException("Reservation not found", Response.Status.NOT_FOUND));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new WebApplicationException("Failed to serialize payload", Response.Status.BAD_REQUEST);
        }
    }
}
