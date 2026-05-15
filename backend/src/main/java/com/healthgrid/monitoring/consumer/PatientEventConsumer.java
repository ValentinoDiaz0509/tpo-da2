package com.healthgrid.monitoring.consumer;

import com.healthgrid.monitoring.dto.PatientDTO;
import com.healthgrid.monitoring.model.PatientStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import java.util.function.Consumer;

/**
 * Consumer for patient events from AWS SQS.
 * Processes incoming patient event messages via Spring Cloud Stream.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PatientEventConsumer {

    /**
     * Consumer bean for patient events.
     * Handles incoming messages from the 'patient-events-queue' SQS queue.
     * The function name maps to the binding in application.yml (patientEventInput).
     *
     * @return Consumer that processes PatientDTO events
     */
    @Bean
    public Consumer<PatientDTO> patientEventInput() {
        return patientEvent -> {
            try {
                log.info("Received patient event for ID: {}, Name: {}", patientEvent.getId(), patientEvent.getName());
                log.info("Patient status: {}", patientEvent.getStatus());
                
                // Process the patient event
                processPatientEvent(patientEvent);
                
                log.info("Patient event processed successfully for ID: {}", patientEvent.getId());
            } catch (Exception e) {
                log.error("Error processing patient event for ID: {}", patientEvent.getId(), e);
                // Implement retry logic or send to DLQ if needed
            }
        };
    }

    /**
     * Process incoming patient event.
     * This method contains the business logic for handling patient events.
     *
     * @param patientEvent the patient event received from the queue
     */
    private void processPatientEvent(PatientDTO patientEvent) {
        log.debug("Processing patient event: {}", patientEvent);
        
        // TODO: Implement business logic
        // Examples:
        // - Update patient status in database
        // - Send notifications
        // - Trigger alerts for critical patients
        // - Update monitoring dashboards
        // - Log audit trail
        
        if (patientEvent.getStatus() != null) {
            switch (patientEvent.getStatus()) {
                case CRITICAL:
                    handleCriticalPatient(patientEvent);
                    break;
                case NORMAL:
                    handleNormalPatient(patientEvent);
                    break;
                case WARNING:
                    handleWarningPatient(patientEvent);
                    break;
                default:
                    log.warn("Unknown patient status: {} for ID: {}", patientEvent.getStatus(), patientEvent.getId());
            }
        }
    }

    private void handleCriticalPatient(PatientDTO patientEvent) {
        log.warn("ALERT: Critical patient detected - ID: {}, Name: {}, Room: {}, Bed: {}", 
            patientEvent.getId(), 
            patientEvent.getName(),
            patientEvent.getRoom(),
            patientEvent.getBed());
        // TODO: Send urgent notification to medical staff
    }

    private void handleNormalPatient(PatientDTO patientEvent) {
        log.info("Patient is stable - ID: {}, Name: {}", patientEvent.getId(), patientEvent.getName());
        // TODO: Update monitoring frequency
    }

    private void handleWarningPatient(PatientDTO patientEvent) {
        log.warn("Patient requires attention - ID: {}, Name: {}, Status: {}", 
            patientEvent.getId(), 
            patientEvent.getName(),
            patientEvent.getStatus());
        // TODO: Notify medical staff for review
    }

}

