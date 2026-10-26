package com.MBQ.controller;

import com.MBQ.dto.QueueDtos.PatientRegistrationRequest;
import com.MBQ.dto.QueueDtos.PatientResponse;
import com.MBQ.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final QueueService queueService;

    public PatientController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse registerPatient(
            @Valid @RequestBody PatientRegistrationRequest request
    ) {
        return queueService.registerPatient(request);
    }
}