package com.MBQ.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "blood_stock",
       uniqueConstraints = @UniqueConstraint(columnNames = {"facility_id", "blood_group"}))
@Getter @Setter
public class BloodStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Facility facility;
    @Enumerated(EnumType.STRING) @Column(name = "blood_group", nullable = false)
    private BloodGroup bloodGroup;
    @Column(nullable = false)
    private int unitsAvailable;
    @Column(nullable = false)
    private int lowStockThreshold = 5;    // FR6: configurable
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @PreUpdate @PrePersist
    void touch() { lastUpdated = LocalDateTime.now(); }
}
