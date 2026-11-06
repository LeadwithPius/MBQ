package com.MBQ.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A blood donation and its screening outcome (V12). */
@Entity
@Table(name = "blood_donation")
@Getter
@Setter
public class BloodDonation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private Donor donor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false, length = 20)
    private BloodGroup bloodGroup;

    @Column(nullable = false)
    private int units = 1;

    @Column(name = "collected_at", nullable = false)
    private OffsetDateTime collectedAt = OffsetDateTime.now();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by", nullable = false)
    private StaffUser collectedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationStatus status = DonationStatus.COLLECTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screened_by")
    private StaffUser screenedBy;

    @Column(name = "screened_at")
    private OffsetDateTime screenedAt;

    @Column(name = "screening_notes", length = 1000)
    private String screeningNotes;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", unique = true)
    private BloodStockBatch batch;
}
