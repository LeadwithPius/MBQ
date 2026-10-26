package com.MBQ.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "triage_observation")
public class TriageObservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_ticket_id")
    private QueueTicket queueTicket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessed_by_id", nullable = false)
    private StaffUser assessedBy;

    @Column(name = "measured_at", nullable = false)
    private OffsetDateTime measuredAt = OffsetDateTime.now();
    @Column(name = "systolic_bp") private Integer systolicBp;
    @Column(name = "diastolic_bp") private Integer diastolicBp;
    @Column(name = "heart_rate") private Integer heartRate;
    @Column(name = "respiratory_rate") private Integer respiratoryRate;
    @Column(name = "temperature_c", precision = 4, scale = 1) private BigDecimal temperatureC;
    @Column(name = "spo2_percent") private Integer spo2Percent;
    @Column(nullable = false, columnDefinition = "TEXT") private String symptoms = "";
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient v) { patient = v; }
    public QueueTicket getQueueTicket() { return queueTicket; }
    public void setQueueTicket(QueueTicket v) { queueTicket = v; }
    public StaffUser getAssessedBy() { return assessedBy; }
    public void setAssessedBy(StaffUser v) { assessedBy = v; }
    public OffsetDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(OffsetDateTime v) { measuredAt = v; }
    public Integer getSystolicBp() { return systolicBp; }
    public void setSystolicBp(Integer v) { systolicBp = v; }
    public Integer getDiastolicBp() { return diastolicBp; }
    public void setDiastolicBp(Integer v) { diastolicBp = v; }
    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer v) { heartRate = v; }
    public Integer getRespiratoryRate() { return respiratoryRate; }
    public void setRespiratoryRate(Integer v) { respiratoryRate = v; }
    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal v) { temperatureC = v; }
    public Integer getSpo2Percent() { return spo2Percent; }
    public void setSpo2Percent(Integer v) { spo2Percent = v; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String v) { symptoms = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { notes = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
