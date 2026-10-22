package com.MBQ.controller;

import com.MBQ.model.Department;
import com.MBQ.model.Patient;
import com.MBQ.repository.DepartmentRepository;
import com.MBQ.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Shared setup for the API tests.
 *
 * - Starts the whole application against the in-memory H2 test database (profile "test").
 * - DataSeeder fills in the demo facility and departments (OPD, Emergency, Maternity) at startup.
 * - @Transactional rolls every test back, so tests never see each other's data.
 * - MockMvc sends fake HTTP requests through the real controllers, validation,
 *   error handler and security filters, without starting a web server.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class ApiTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected DepartmentRepository departmentRepository;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Id of a seeded department by name, e.g. "OPD". */
    protected Long departmentId(String name) {
        return departmentRepository.findAll().stream()
                .filter(d -> d.getName().equals(name))
                .map(Department::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seeded department not found: " + name));
    }

    /** Saves a patient directly in the database (quicker than going through the API). */
    protected Long savePatient(String name) {
        Patient p = new Patient();
        p.setName(name);
        p.setAge(30);
        p.setSex("FEMALE");
        return patientRepository.save(p).getId();
    }
}
