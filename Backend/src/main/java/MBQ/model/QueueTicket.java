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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "queue_ticket") @Getter @Setter
public class QueueTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String ticketNumber;          // e.g. OPD-0012, generated per dept per day
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Patient patient;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Department department;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PriorityLevel priorityLevel = PriorityLevel.NORMAL;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TicketStatus status = TicketStatus.WAITING;
    @Column(nullable = false)
    private LocalDateTime timeIssued = LocalDateTime.now();
    private String triageNotes;
    private String outcome;
    private boolean requiresBlood;
}
