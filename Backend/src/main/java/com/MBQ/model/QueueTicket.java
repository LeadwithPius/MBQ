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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "queue_ticket")
@Getter
@Setter
public class QueueTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, length = 255)
    private String ticketNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_level", nullable = false, length = 255)
    private PriorityLevel priorityLevel = PriorityLevel.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 255)
    private TicketStatus status = TicketStatus.WAITING;

    @Column(name = "time_issued", nullable = false)
    private LocalDateTime timeIssued;

    @Column(name = "triage_notes", length = 255)
    private String triageNotes;

    @Column(name = "outcome", length = 255)
    private String outcome;

    @Column(name = "requires_blood", nullable = false)
    private boolean requiresBlood;
}