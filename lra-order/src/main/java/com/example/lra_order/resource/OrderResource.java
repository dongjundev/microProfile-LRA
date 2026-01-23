package com.example.lra_order.resource;

import com.example.lra_order.dto.InventoryRequest;
import com.example.lra_order.dto.InventoryResponse;
import com.example.lra_order.dto.OrderRequest;
import com.example.lra_order.dto.OrderResponse;
import com.example.lra_order.dto.PaymentRequest;
import com.example.lra_order.dto.PaymentResponse;
import com.example.lra_order.entity.OrderEntity;
import com.example.lra_order.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import org.glassfish.jersey.client.JerseyClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Component
@Path("/orders")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {
    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final Client client;

    @Value("${inventory.base-url}")
    private String inventoryBaseUrl;

    @Value("${payment.base-url}")
    private String paymentBaseUrl;

    public OrderResource(OrderRepository orderRepository,
                         ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.client = new JerseyClientBuilder().build();
    }

    @POST
    @LRA(value = LRA.Type.REQUIRES_NEW, end = true)
    public OrderResponse createOrder(OrderRequest request,
                                     @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        String orderId = Optional.ofNullable(request.orderId()).orElseGet(() -> UUID.randomUUID().toString());
        String requestJson = toJson(request);

        // Narayana automatically started LRA and provided lraId via header
        log.info("Order processing started: orderId={} lraId={}", orderId, lraId);

        OrderEntity entity = new OrderEntity(orderId, "PENDING", requestJson);
        if (lraId != null) {
            entity.setLraId(lraId.toString());
        }
        orderRepository.save(entity);

        String inventoryStatus = "SKIPPED";
        String paymentStatus = "SKIPPED";

        try {
            InventoryRequest inventoryRequest = new InventoryRequest(orderId, request.items(), request.failInventory());
            String inventoryResponseJson = callParticipant(inventoryBaseUrl + "/inventory/reserve", lraId, inventoryRequest);
            InventoryResponse inventoryResponse = objectMapper.readValue(inventoryResponseJson, InventoryResponse.class);
            if ("FAILED".equalsIgnoreCase(inventoryResponse.status())) {
                inventoryStatus = "FAILED";
                throw new WebApplicationException("Inventory reservation failed");
            }
            inventoryStatus = "RESERVED";

            PaymentRequest paymentRequest = new PaymentRequest(orderId, request.amount(), request.failPayment());
            String paymentResponseJson = callParticipant(paymentBaseUrl + "/payment/authorize", lraId, paymentRequest);
            PaymentResponse paymentResponse = objectMapper.readValue(paymentResponseJson, PaymentResponse.class);
            if ("FAILED".equalsIgnoreCase(paymentResponse.status())) {
                paymentStatus = "FAILED";
                throw new WebApplicationException("Payment authorization failed");
            }
            paymentStatus = "AUTHORIZED";

            entity.setStatus("CONFIRMED");
            entity.setInventoryStatus(inventoryStatus);
            entity.setPaymentStatus(paymentStatus);
            orderRepository.save(entity);

            log.info("Order completed successfully: orderId={} lraId={}", orderId, lraId);

            return new OrderResponse(orderId, entity.getStatus(), entity.getLraId(),
                    inventoryStatus, paymentStatus);
        } catch (Exception ex) {
            log.error("Order failed: orderId={} lraId={}", orderId, lraId, ex);

            entity.setStatus("CANCELLED");
            entity.setInventoryStatus(inventoryStatus.equals("RESERVED") ? "COMPENSATING" : inventoryStatus);
            entity.setPaymentStatus(paymentStatus.equals("AUTHORIZED") ? "COMPENSATING" : paymentStatus);
            orderRepository.save(entity);
            throw new WebApplicationException("Order failed: " + ex.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/{orderId}")
    public OrderResponse getOrder(@PathParam("orderId") String orderId) {
        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new WebApplicationException("Order not found", Response.Status.NOT_FOUND));

        return new OrderResponse(
                entity.getOrderId(),
                entity.getStatus(),
                entity.getLraId(),
                entity.getInventoryStatus(),
                entity.getPaymentStatus()
        );
    }

    private String callParticipant(String url, URI lraId, Object payload) {
        try (Response response = client.target(url)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(LRA_HTTP_CONTEXT_HEADER, lraId)
                .post(Entity.json(payload))) {
            if (response.getStatus() >= 300) {
                String body = response.hasEntity() ? response.readEntity(String.class) : "";
                log.error("Participant call failed: url={} status={} body={}", url, response.getStatus(), body);
                throw new WebApplicationException("Participant call failed: " + response.getStatus(),
                        Response.Status.fromStatusCode(response.getStatus()));
            }
            return response.hasEntity() ? response.readEntity(String.class) : "";
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new WebApplicationException("Failed to serialize payload", Response.Status.BAD_REQUEST);
        }
    }
}
