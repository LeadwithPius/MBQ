package com.MBQ.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatientApiTest extends ApiTestBase {

    @Test
    void registerPatient_validRequest_returns201WithLocationAndBody() throws Exception {
        String body = """
                {
                  "name": "  Mwaba Banda ",
                  "age": 34,
                  "sex": "female",
                  "contact": "+260971234567",
                  "bloodGroup": "O_POS"
                }""";

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/patients/\\d+$")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Mwaba Banda"))      // trimmed
                .andExpect(jsonPath("$.age").value(34))
                .andExpect(jsonPath("$.sex").value("FEMALE"))            // normalised to upper case
                .andExpect(jsonPath("$.contact").value("+260971234567"))
                .andExpect(jsonPath("$.bloodGroup").value("O_POS"));
    }

    @Test
    void registerPatient_missingAndInvalidFields_returns400WithFieldErrors() throws Exception {
        String body = """
                {
                  "name": "",
                  "age": -3,
                  "sex": "unknown",
                  "contact": "abc"
                }""";

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.age").value("Age cannot be negative"))
                .andExpect(jsonPath("$.fieldErrors.sex").value("Sex must be MALE or FEMALE"))
                .andExpect(jsonPath("$.fieldErrors.contact", notNullValue()));
    }

    @Test
    void registerPatient_unknownBloodGroup_returns400() throws Exception {
        String body = """
                {"name": "Test Patient", "age": 20, "sex": "MALE", "bloodGroup": "Z_POS"}""";

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.path").value("/api/patients"));
    }

    @Test
    void registerPatient_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerPatient_unknownFacility_returns404() throws Exception {
        String body = """
                {"name": "Test Patient", "age": 20, "sex": "MALE", "facilityId": 999999}""";

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Facility with id 999999 was not found"));
    }

    @Test
    void getPatient_existing_returns200() throws Exception {
        Long id = savePatient("Chanda Mulenga");

        mockMvc.perform(get("/api/patients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Chanda Mulenga"));
    }

    @Test
    void getPatient_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/patients/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Patient with id 999999 was not found"));
    }

    @Test
    void getPatient_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/patients/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchPatients_byName_isCaseInsensitive() throws Exception {
        savePatient("Bwalya Phiri");
        savePatient("Natasha Phiri");
        savePatient("John Tembo");

        mockMvc.perform(get("/api/patients").param("name", "phiri"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Bwalya Phiri"))   // A-Z order
                .andExpect(jsonPath("$[1].name").value("Natasha Phiri"));
    }
}
