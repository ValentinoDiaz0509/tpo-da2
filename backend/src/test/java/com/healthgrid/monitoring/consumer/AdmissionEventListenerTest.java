package com.healthgrid.monitoring.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthgrid.monitoring.dto.MonitoreoWebhookRequestDTO;
import com.healthgrid.monitoring.service.MonitoringAdmissionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link AdmissionEventListener#onMessage(String)} with the exact wire format M6 publishes,
 * exercising the Core-envelope + JSON-string-payload double parse end-to-end (real ObjectMapper).
 *
 * Uses a capturing subclass rather than a Mockito mock: the service is a concrete class and the
 * inline mock maker cannot redefine it on this JVM.
 */
class AdmissionEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /** Records the arguments the listener forwards after unwrapping the Core envelope. */
    private static class CapturingAdmissionService extends MonitoringAdmissionService {
        Integer capturedEventTypeId;
        String capturedEventTypeName;
        MonitoreoWebhookRequestDTO capturedPayload;

        CapturingAdmissionService() {
            super(null); // PatientRepository unused: handleEvent is overridden
        }

        @Override
        public void handleEvent(Integer eventTypeId, String eventTypeName, MonitoreoWebhookRequestDTO request) {
            this.capturedEventTypeId = eventTypeId;
            this.capturedEventTypeName = eventTypeName;
            this.capturedPayload = request;
        }
    }

    /** Exact raw message the Core delivers: payload is a JSON *string* (double-encoded). */
    private String rawEvent(int eventTypeId) {
        return """
            {
              "event_type_id": %d,
              "event_type_name": "monitoring.requested",
              "publisher_module": "internacion",
              "payload": "{\\"paciente_id\\":123,\\"cama_id\\":45,\\"timestamp\\":\\"2026-07-17T14:05:00Z\\"}"
            }
            """.formatted(eventTypeId);
    }

    @Test
    void parsesBajaEnvelopeAndForwardsIdAndPayload() {
        CapturingAdmissionService service = new CapturingAdmissionService();
        AdmissionEventListener listener = new AdmissionEventListener(objectMapper, service);

        listener.onMessage(rawEvent(27));

        assertThat(service.capturedEventTypeId).isEqualTo(27);
        assertThat(service.capturedPayload.getPacienteId()).isEqualTo(123L);
        assertThat(service.capturedPayload.getCamaId()).isEqualTo(45L);
        assertThat(service.capturedPayload.getTimestamp()).isNotNull();
    }

    @Test
    void parsesAltaEnvelope() {
        CapturingAdmissionService service = new CapturingAdmissionService();
        AdmissionEventListener listener = new AdmissionEventListener(objectMapper, service);

        listener.onMessage(rawEvent(26));

        assertThat(service.capturedEventTypeId).isEqualTo(26);
        assertThat(service.capturedPayload.getPacienteId()).isEqualTo(123L);
    }
}
