package com.MBQ.service;

import com.MBQ.model.TriageObservation;
import java.util.Optional;

/**
 * Future integration point only. No AI model is activated by this interface.
 * Any implementation must be clinically validated, audited, versioned and
 * approved for the intended patient population before use.
 */
public interface AiRiskAssessmentProvider {
    boolean isEnabled();
    Optional<Result> assess(TriageObservation observation);

    record Result(String modelName, String modelVersion, String summary,
                  String validationReference) {}
}
