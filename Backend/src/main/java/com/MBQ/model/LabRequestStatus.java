package com.MBQ.model;

/** REQUESTED -> IN_PROGRESS -> COMPLETED; REQUESTED/IN_PROGRESS -> CANCELLED. COMPLETED and CANCELLED are final. */
public enum LabRequestStatus {
    REQUESTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
