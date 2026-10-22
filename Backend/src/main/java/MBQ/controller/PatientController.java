package com.MBQ.controller;

import com.MBQ.dto.PatientResponse;
import com.MBQ.dto.RegisterPatientRequest;
import com.MBQ.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Reception endpoints. Controllers only translate HTTP <-> Java;
 * all the rules live in {@link PatientService}.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /** POST /api/patients -> 201 Created, with a Location header pointing at the new patient. */
    @PostMapping
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        PatientResponse created = patientService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** GET /api/patients/{id} -> 200, or 404 if no such patient. */
    @GetMapping("/{id}")
    public PatientResponse getById(@PathVariable("id") Long id) {
        return patientService.getById(id);
    }

    /** GET /api/patients?name=ban -> up to 50 matches; without name -> 50 most recent. */
    @GetMapping
    public List<PatientResponse> search(@RequestParam(name = "name", required = false) String name) {
        return patientService.search(name);
    }
}
