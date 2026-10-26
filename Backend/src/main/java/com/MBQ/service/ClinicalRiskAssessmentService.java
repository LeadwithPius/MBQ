package com.MBQ.service;

import com.MBQ.model.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ClinicalRiskAssessmentService {
    public record RuleResult(PriorityLevel priority, List<String> findings, String version) {}

    /**
     * Development baseline only, NOT a validated clinical protocol.
     * A NORMAL result never rules out a serious condition or cardiac event.
     */
    public RuleResult assess(TriageObservation o) {
        List<String> findings = new ArrayList<>();
        boolean emergency = false, urgent = false;
        Integer spo2 = o.getSpo2Percent(), systolic = o.getSystolicBp();
        Integer diastolic = o.getDiastolicBp(), pulse = o.getHeartRate();
        Integer rr = o.getRespiratoryRate();
        String symptoms = o.getSymptoms() == null ? "" : o.getSymptoms().toLowerCase(Locale.ROOT);

        if (spo2 != null && spo2 < 90) {
            emergency = true; findings.add("SpO2 below 90%: immediate clinical assessment indicated.");
        } else if (spo2 != null && spo2 < 94) {
            urgent = true; findings.add("SpO2 below 94%: prompt clinical review indicated.");
        }
        if (systolic != null && systolic < 90) {
            emergency = true; findings.add("Systolic blood pressure below 90 mmHg.");
        }
        if ((systolic != null && systolic >= 180) || (diastolic != null && diastolic >= 120)) {
            urgent = true; findings.add("Markedly elevated blood pressure: prompt clinician assessment indicated.");
        }
        if (pulse != null && (pulse > 130 || pulse < 40)) {
            emergency = true; findings.add("Heart rate at an extreme value.");
        } else if (pulse != null && (pulse > 120 || pulse < 50)) {
            urgent = true; findings.add("Heart rate outside the baseline expected range.");
        }
        if (rr != null && (rr > 30 || rr < 8)) {
            emergency = true; findings.add("Respiratory rate at an extreme value.");
        } else if (rr != null && (rr > 24 || rr < 10)) {
            urgent = true; findings.add("Respiratory rate outside the baseline expected range.");
        }
        BigDecimal temp = o.getTemperatureC();
        if (temp != null && (temp.compareTo(new BigDecimal("40.0")) >= 0
                || temp.compareTo(new BigDecimal("35.0")) < 0)) {
            urgent = true; findings.add("Temperature markedly high or low.");
        }

        boolean chest = symptoms.contains("chest pain") || symptoms.contains("chest tightness")
                || symptoms.contains("chest pressure");
        boolean warning = symptoms.contains("shortness of breath") || symptoms.contains("breathlessness")
                || symptoms.contains("sweating") || symptoms.contains("faint")
                || symptoms.contains("collapse") || symptoms.contains("jaw pain")
                || symptoms.contains("arm pain") || symptoms.contains("pain radiating");
        if (chest && warning) {
            emergency = true;
            findings.add("Chest discomfort with another warning symptom: immediate clinical assessment; not a diagnosis.");
        } else if (chest) {
            urgent = true;
            findings.add("Chest discomfort reported: prompt clinician assessment; this rule cannot exclude a cardiac event.");
        }
        if (findings.isEmpty()) {
            findings.add("No baseline rule trigger detected in supplied values. Serious illness is not excluded.");
        }
        PriorityLevel priority = emergency ? PriorityLevel.EMERGENCY
                : urgent ? PriorityLevel.URGENT : PriorityLevel.NORMAL;
        return new RuleResult(priority, List.copyOf(findings), "RULES_BASELINE_V1");
    }

    public ClinicalRiskAssessment toAssessment(TriageObservation o, RuleResult r) {
        ClinicalRiskAssessment a = new ClinicalRiskAssessment();
        a.setObservation(o);
        a.setRecommendedPriority(r.priority());
        a.setFindings(String.join("\n", r.findings()));
        a.setAssessmentSource("RULES_BASELINE");
        a.setModelVersion(r.version());
        a.setClinicianReviewRequired(true);
        a.setReviewStatus(ClinicalRiskAssessment.ReviewStatus.PENDING);
        return a;
    }
}
