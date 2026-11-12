package com.MBQ.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
public class Patient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private Integer age;                  // ERD has Age_Sex; FR1 lists them separately
    @Column(name = "nrc_number", length = 20)
    private String nrcNumber;
    private String sex;
    private String contact;
    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Facility facility;
}
