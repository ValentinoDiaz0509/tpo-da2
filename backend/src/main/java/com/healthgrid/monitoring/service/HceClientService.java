package com.healthgrid.monitoring.service;

import com.healthgrid.monitoring.dto.hce.AntecedenteDTO;
import com.healthgrid.monitoring.dto.hce.FichaMedicaDTO;
import com.healthgrid.monitoring.dto.hce.PatientHceSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HceClientService {

    private final RestTemplate restTemplate;
    private final CoreAuthService coreAuthService;

    @Value("${healthgrid.module1.hce.url:http://localhost:8082}")
    private String hceUrl;

    @Value("${healthgrid.module1.hce.enabled:true}")
    private boolean hceEnabled;

    /**
     * Gets the full HCE summary for a patient, heavily cached since this changes rarely
     * compared to telemetry frequency.
     */
    @Cacheable(value = "patientHce", key = "#externalId", unless = "#result == null")
    public PatientHceSummaryDTO getPatientHceSummary(String externalId) {
        if (!hceEnabled || externalId == null || externalId.isEmpty()) {
            log.debug("HCE Integration is disabled or externalId is null. Using default thresholds.");
            return null; // Return null to use standard thresholds
        }

        Integer idPaciente;
        try {
            idPaciente = Integer.parseInt(externalId);
        } catch (NumberFormatException e) {
            log.warn("Invalid externalId '{}', must be an integer for HCE.", externalId);
            return null;
        }

        try {
            String token = coreAuthService.getValidToken();
            if (token == null) {
                log.warn("Cannot fetch HCE for patient {} because auth token is null. Using standard thresholds.", idPaciente);
                return null;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<?> requestEntity = new HttpEntity<>(headers);

            // Fetch Ficha Medica
            FichaMedicaDTO ficha = null;
            try {
                ResponseEntity<FichaMedicaDTO> response = restTemplate.exchange(
                        hceUrl + "/api/v1/pacientes/" + idPaciente + "/ficha-medica",
                        HttpMethod.GET,
                        requestEntity,
                        FichaMedicaDTO.class
                );
                if (response.getStatusCode().is2xxSuccessful()) {
                    ficha = response.getBody();
                }
            } catch (Exception ex) {
                log.warn("Could not fetch Ficha Medica for patient {}: {}", idPaciente, ex.getMessage());
                // Non-fatal, patient might just not have a ficha medica yet
            }

            // Fetch Antecedentes
            List<AntecedenteDTO> antecedentes = Collections.emptyList();
            try {
                ResponseEntity<AntecedenteDTO[]> response = restTemplate.exchange(
                        hceUrl + "/api/v1/pacientes/" + idPaciente + "/antecedentes",
                        HttpMethod.GET,
                        requestEntity,
                        AntecedenteDTO[].class
                );
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    antecedentes = Arrays.asList(response.getBody());
                }
            } catch (Exception ex) {
                log.warn("Could not fetch Antecedentes for patient {}: {}", idPaciente, ex.getMessage());
            }

            if (ficha == null && antecedentes.isEmpty()) {
                log.debug("No HCE data found for patient {}.", idPaciente);
                return null;
            }

            log.info("✓ Successfully fetched HCE for patient {}", idPaciente);
            return PatientHceSummaryDTO.builder()
                    .fichaMedica(ficha)
                    .antecedentes(antecedentes)
                    .build();

        } catch (Exception e) {
            log.error("Failed to communicate with Módulo 1 (HCE). Using standard thresholds.", e);
            return null;
        }
    }
}
