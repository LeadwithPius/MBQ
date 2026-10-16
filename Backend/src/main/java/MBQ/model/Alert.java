package com.MBQ.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Simulated donor alert: logged only, no real SMS. */
@Entity @Getter @Setter
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private BloodStock stock;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Donor donor;
    private LocalDateTime dateSent = LocalDateTime.now();
    private String responseStatus = "PENDING";
}
