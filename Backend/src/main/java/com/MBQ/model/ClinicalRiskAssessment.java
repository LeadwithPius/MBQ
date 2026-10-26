package com.MBQ.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "clinical_risk_assessment")
public class ClinicalRiskAssessment {
    public enum ReviewStatus { PENDING, CONFIRMED, OVERRIDDEN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "observation_id", nullable = false)
    private TriageObservation observation;
    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_priority", nullable = false, length = 20)
    private PriorityLevel recommendedPriority;
    @Column(nullable = false, columnDefinition = "TEXT") private String findings;
    @Column(name = "assessment_source", nullable = false, length = 40)
    private String assessmentSource = "RULES_BASELINE";
    @Column(name = "model_version", nullable = false, length = 80)
    private String modelVersion = "RULES_BASELINE_V1";
    @Column(name = "clinician_review_required", nullable = false)
    private boolean clinicianReviewRequired = true;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private StaffUser reviewedBy;
    @Column(name = "clinician_notes", columnDefinition = "TEXT") private String clinicianNotes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;

    public Long getId() { return id; }
    public TriageObservation getObservation() { return observation; }
    public void setObservation(TriageObservation v) { observation = v; }
    public PriorityLevel getRecommendedPriority() { return recommendedPriority; }
    public void setRecommendedPriority(PriorityLevel v) { recommendedPriority = v; }
    public String getFindings() { return findings; }
    public void setFindings(String v) { findings = v; }
    public String getAssessmentSource() { return assessmentSource; }
    public void setAssessmentSource(String v) { assessmentSource = v; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String v) { modelVersion = v; }
    public boolean isClinicianReviewRequired() { return clinicianReviewRequired; }
    public void setClinicianReviewRequired(boolean v) { clinicianReviewRequired = v; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus v) { reviewStatus = v; }
    public StaffUser getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(StaffUser v) { reviewedBy = v; }
    public String getClinicianNotes() { return clinicianNotes; }
    public void setClinicianNotes(String v) { clinicianNotes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime v) { reviewedAt = v; }
}
