package com.MBQ.model;

import java.time.LocalDateTime;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "blood_stock",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_blood_stock_facility_group",
            columnNames = {"facility_id", "blood_group"}
        )
    }
)
@Getter
@Setter
public class BloodStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false, length = 255)
    private BloodGroup bloodGroup;

    @Column(name = "units_available", nullable = false)
    private int unitsAvailable;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onCreate() {
        updateTimestamp();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTimestamp();
    }

    private void updateTimestamp() {
        lastUpdated = LocalDateTime.now();
    }
}
