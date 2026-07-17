package com.healthgrid.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * External message envelope delivered by the M10 Core event bus over RabbitMQ.
 *
 * The Core wraps every published event in this structure. Note that {@link #payload}
 * arrives as a JSON <b>string</b> (double-encoded): first deserialize this envelope,
 * then parse {@code payload} into the concrete event DTO (e.g. MonitoreoWebhookRequestDTO).
 *
 * Example:
 * <pre>
 * {
 *   "event_type_id": 42,
 *   "event_type_name": "monitoring.admission.requested",
 *   "source_module": "monitoring",
 *   "publisher_module": "internacion",
 *   "log_id": 456,
 *   "payload": "{\"evento\":\"ALTA_MONITOREO\",\"paciente_id\":123}",
 *   "published_at": "2026-07-17T12:00:00Z"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoreEventEnvelope {

    @JsonProperty("event_type_id")
    private Integer eventTypeId;

    @JsonProperty("event_type_name")
    private String eventTypeName;

    @JsonProperty("source_module")
    private String sourceModule;

    @JsonProperty("publisher_module")
    private String publisherModule;

    @JsonProperty("log_id")
    private Long logId;

    /** Inner business payload, encoded as a JSON string. Parse separately. */
    @JsonProperty("payload")
    private String payload;

    @JsonProperty("published_at")
    private String publishedAt;
}
