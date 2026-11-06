package com.MBQ.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.MBQ.model.Consultation;

public interface ConsultationRepository extends JpaRepository<Consultation, Long> {

    Optional<Consultation> findByQueueTicket_Id(Long queueTicketId);

    List<Consultation> findByPatient_IdOrderByCreatedAtDesc(Long patientId);
}
