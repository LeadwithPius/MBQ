package com.MBQ.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class QueueControllerAuthorizationTest {

    @Test
    void nurseIsAuthorizedToCallNextPatient() throws NoSuchMethodException {
        PreAuthorize authorization = QueueController.class
                .getMethod("callNextPatient", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertTrue(authorization.value().contains("'NURSE'"));
    }
}
