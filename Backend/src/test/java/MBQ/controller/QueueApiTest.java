package com.MBQ.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueApiTest extends ApiTestBase {

    // ---------- helpers ----------

    private ResultActions createTicket(Long patientId, Long departmentId, String priority) throws Exception {
        String priorityJson = priority == null ? "" : ", \"priorityLevel\": \"" + priority + "\"";
        String body = "{\"patientId\": " + patientId + ", \"departmentId\": " + departmentId + priorityJson + "}";
        return mockMvc.perform(post("/api/queue/tickets").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Creates a ticket through the API and returns its id. */
    private Long createTicketId(Long patientId, Long departmentId, String priority) throws Exception {
        MvcResult result = createTicket(patientId, departmentId, priority)
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private ResultActions patchJson(String url, String body) throws Exception {
        return mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---------- creating tickets ----------

    @Test
    void createTicket_valid_returns201WaitingTicketWithNumberAndPosition() throws Exception {
        Long patient = savePatient("Mutale Zulu");
        Long opd = departmentId("OPD");

        createTicket(patient, opd, null)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/queue/tickets/\\d+$")))
                .andExpect(jsonPath("$.ticketNumber").value("OPD-001"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.priorityLevel").value("NORMAL"))
                .andExpect(jsonPath("$.patientName").value("Mutale Zulu"))
                .andExpect(jsonPath("$.departmentName").value("OPD"))
                .andExpect(jsonPath("$.requiresBlood").value(false))
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void createTicket_numbersIncreasePerDepartment() throws Exception {
        Long opd = departmentId("OPD");
        Long emergency = departmentId("Emergency");

        createTicket(savePatient("A"), opd, null).andExpect(jsonPath("$.ticketNumber").value("OPD-001"));
        createTicket(savePatient("B"), opd, null).andExpect(jsonPath("$.ticketNumber").value("OPD-002"));
        createTicket(savePatient("C"), emergency, null).andExpect(jsonPath("$.ticketNumber").value("EME-001"));
    }

    @Test
    void createTicket_patientAlreadyHasActiveTicket_returns409() throws Exception {
        Long patient = savePatient("Duplicate Person");
        Long opd = departmentId("OPD");
        createTicketId(patient, opd, null);

        createTicket(patient, departmentId("Maternity"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createTicket_unknownPatient_returns404() throws Exception {
        createTicket(999999L, departmentId("OPD"), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Patient with id 999999 was not found"));
    }

    @Test
    void createTicket_unknownDepartment_returns404() throws Exception {
        createTicket(savePatient("Lost Patient"), 999999L, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Department with id 999999 was not found"));
    }

    @Test
    void createTicket_missingIds_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/queue/tickets").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.patientId").value("patientId is required"))
                .andExpect(jsonPath("$.fieldErrors.departmentId").value("departmentId is required"));
    }

    @Test
    void createTicket_invalidPriority_returns400() throws Exception {
        createTicket(savePatient("X"), departmentId("OPD"), "SUPER_URGENT")
                .andExpect(status().isBadRequest());
    }

    // ---------- reading tickets and the live queue ----------

    @Test
    void getTicket_existing_returns200_unknown_returns404() throws Exception {
        Long ticket = createTicketId(savePatient("Reader"), departmentId("OPD"), null);

        mockMvc.perform(get("/api/queue/tickets/{id}", ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket))
                .andExpect(jsonPath("$.status").value("WAITING"));

        mockMvc.perform(get("/api/queue/tickets/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void departmentQueue_ordersEmergencyThenUrgentThenNormal_thenByArrival() throws Exception {
        Long opd = departmentId("OPD");
        createTicketId(savePatient("Normal First"), opd, "NORMAL");
        createTicketId(savePatient("Normal Second"), opd, "NORMAL");
        createTicketId(savePatient("Urgent"), opd, "URGENT");
        createTicketId(savePatient("Emergency"), opd, "EMERGENCY");

        mockMvc.perform(get("/api/queue/departments/{id}", opd))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("OPD"))
                .andExpect(jsonPath("$.waitingCount").value(4))
                .andExpect(jsonPath("$.nowServing", hasSize(0)))
                .andExpect(jsonPath("$.waiting[0].patientName").value("Emergency"))
                .andExpect(jsonPath("$.waiting[0].position").value(1))
                .andExpect(jsonPath("$.waiting[1].patientName").value("Urgent"))
                .andExpect(jsonPath("$.waiting[2].patientName").value("Normal First"))
                .andExpect(jsonPath("$.waiting[3].patientName").value("Normal Second"))
                .andExpect(jsonPath("$.waiting[3].position").value(4));
    }

    @Test
    void departmentQueue_unknownDepartment_returns404() throws Exception {
        mockMvc.perform(get("/api/queue/departments/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void listTickets_filtersByDepartmentAndStatus() throws Exception {
        Long opd = departmentId("OPD");
        Long first = createTicketId(savePatient("One"), opd, null);
        createTicketId(savePatient("Two"), opd, null);
        createTicketId(savePatient("Three"), departmentId("Maternity"), null);
        patchJson("/api/queue/tickets/" + first + "/status", "{\"status\": \"IN_PROGRESS\"}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/queue/tickets").param("departmentId", opd.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/queue/tickets")
                        .param("departmentId", opd.toString())
                        .param("status", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].patientName").value("Two"));

        mockMvc.perform(get("/api/queue/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void listTickets_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/queue/tickets").param("status", "SLEEPING"))
                .andExpect(status().isBadRequest());
    }

    // ---------- call next ----------

    @Test
    void callNext_takesHighestPriorityAndMovesItToNowServing() throws Exception {
        Long opd = departmentId("OPD");
        createTicketId(savePatient("Normal"), opd, "NORMAL");
        createTicketId(savePatient("Emergency"), opd, "EMERGENCY");

        mockMvc.perform(post("/api/queue/departments/{id}/call-next", opd))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientName").value("Emergency"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.position", nullValue()));

        mockMvc.perform(get("/api/queue/departments/{id}", opd))
                .andExpect(jsonPath("$.nowServing", hasSize(1)))
                .andExpect(jsonPath("$.nowServing[0].patientName").value("Emergency"))
                .andExpect(jsonPath("$.waitingCount").value(1))
                .andExpect(jsonPath("$.waiting[0].patientName").value("Normal"))
                .andExpect(jsonPath("$.waiting[0].position").value(1));
    }

    @Test
    void callNext_emptyQueue_returns204() throws Exception {
        mockMvc.perform(post("/api/queue/departments/{id}/call-next", departmentId("Maternity")))
                .andExpect(status().isNoContent());
    }

    // ---------- status updates ----------

    @Test
    void updateStatus_fullHappyPath_waitingToInProgressToCompleted() throws Exception {
        Long ticket = createTicketId(savePatient("Happy Path"), departmentId("OPD"), null);

        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"IN_PROGRESS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        patchJson("/api/queue/tickets/" + ticket + "/status",
                "{\"status\": \"COMPLETED\", \"outcome\": \"Prescribed antibiotics, discharged\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("Prescribed antibiotics, discharged"))
                .andExpect(jsonPath("$.position", nullValue()));
    }

    @Test
    void updateStatus_invalidTransition_returns409() throws Exception {
        Long ticket = createTicketId(savePatient("Skipper"), departmentId("OPD"), null);

        // cannot complete a ticket that was never called
        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"COMPLETED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(matchesPattern(".*from WAITING to COMPLETED.*")));
    }

    @Test
    void updateStatus_finishedTicketCannotReopen_returns409() throws Exception {
        Long ticket = createTicketId(savePatient("Cancelled"), departmentId("OPD"), null);
        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"CANCELLED\"}")
                .andExpect(status().isOk());

        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"WAITING\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_missingStatus_returns400() throws Exception {
        Long ticket = createTicketId(savePatient("No Status"), departmentId("OPD"), null);

        patchJson("/api/queue/tickets/" + ticket + "/status", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("status is required"));
    }

    @Test
    void updateStatus_unknownTicket_returns404() throws Exception {
        patchJson("/api/queue/tickets/999999/status", "{\"status\": \"IN_PROGRESS\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void completedPatient_canGetANewTicket() throws Exception {
        Long patient = savePatient("Returning");
        Long opd = departmentId("OPD");
        Long ticket = createTicketId(patient, opd, null);
        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"IN_PROGRESS\"}");
        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"COMPLETED\"}");

        createTicket(patient, opd, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNumber").value("OPD-002"));
    }

    // ---------- priority management ----------

    @Test
    void updatePriority_flagEmergency_movesTicketToFront() throws Exception {
        Long opd = departmentId("OPD");
        createTicketId(savePatient("Early Arrival"), opd, null);
        Long late = createTicketId(savePatient("Collapsed"), opd, null);

        patchJson("/api/queue/tickets/" + late + "/priority",
                "{\"priorityLevel\": \"EMERGENCY\", \"triageNotes\": \"Severe bleeding\", \"requiresBlood\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorityLevel").value("EMERGENCY"))
                .andExpect(jsonPath("$.triageNotes").value("Severe bleeding"))
                .andExpect(jsonPath("$.requiresBlood").value(true))
                .andExpect(jsonPath("$.position").value(1));

        mockMvc.perform(get("/api/queue/departments/{id}", opd))
                .andExpect(jsonPath("$.waiting[0].patientName").value("Collapsed"))
                .andExpect(jsonPath("$.waiting[1].patientName").value("Early Arrival"))
                .andExpect(jsonPath("$.waiting[1].position").value(2));
    }

    @Test
    void updatePriority_completedTicket_returns409() throws Exception {
        Long ticket = createTicketId(savePatient("Done"), departmentId("OPD"), null);
        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"IN_PROGRESS\"}");
        patchJson("/api/queue/tickets/" + ticket + "/status", "{\"status\": \"COMPLETED\"}");

        patchJson("/api/queue/tickets/" + ticket + "/priority", "{\"priorityLevel\": \"URGENT\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void updatePriority_missingPriority_returns400() throws Exception {
        Long ticket = createTicketId(savePatient("Blank"), departmentId("OPD"), null);

        patchJson("/api/queue/tickets/" + ticket + "/priority", "{\"triageNotes\": \"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priorityLevel").value("priorityLevel is required"));
    }
}
