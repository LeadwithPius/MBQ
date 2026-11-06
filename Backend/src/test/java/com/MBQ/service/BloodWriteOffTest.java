package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import com.MBQ.dto.BloodInventoryDtos.InventoryOperationResponse;
import com.MBQ.dto.BloodInventoryDtos.WriteOffRequest;
import com.MBQ.dto.BloodInventoryDtos.WriteOffType;
import com.MBQ.model.BloodBatchVerificationStatus;
import com.MBQ.model.BloodGroup;
import com.MBQ.model.BloodInventoryOperation;
import com.MBQ.model.BloodStock;
import com.MBQ.model.BloodStockBatch;
import com.MBQ.model.BloodStockMovement;
import com.MBQ.model.BloodStockMovementType;
import com.MBQ.model.Facility;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.BloodInventoryOperationRepository;
import com.MBQ.repository.BloodStockBatchRepository;
import com.MBQ.repository.BloodStockMovementRepository;
import com.MBQ.repository.BloodStockRepository;
import com.MBQ.repository.PatientRepository;

/** Expired / wasted write-offs keep the stock total equal to usable units, never below zero. */
@ExtendWith(MockitoExtension.class)
class BloodWriteOffTest {

    @Mock private BloodStockRepository stocks;
    @Mock private BloodStockBatchRepository batches;
    @Mock private BloodStockMovementRepository movements;
    @Mock private BloodInventoryOperationRepository operations;
    @Mock private StaffAccessService staffAccess;
    @Mock private JdbcTemplate jdbc;
    @Mock private PatientRepository patients;

    private BloodInventoryService service;
    private BloodStock stock;
    private BloodStockBatch batch;

    @BeforeEach
    void setUp() {
        service = new BloodInventoryService(stocks, batches, movements, operations, staffAccess, jdbc,
                JsonMapper.builder().build(), patients);
        Facility facility = new Facility();
        facility.setId(1L);
        stock = new BloodStock();
        stock.setId(8L);
        stock.setFacility(facility);
        stock.setBloodGroup(BloodGroup.O_POS);
        stock.setUnitsAvailable(10);
        batch = new BloodStockBatch();
        batch.setId(30L);
        batch.setStock(stock);
        batch.setBatchReference("B-30");
        batch.setVerificationStatus(BloodBatchVerificationStatus.VERIFIED);
        batch.setOpeningQuantity(5);
        batch.setRemainingQuantity(5);

        when(staffAccess.currentStaff()).thenReturn(new StaffUser() {{ setId(20L); }});
        when(batches.findById(30L)).thenReturn(Optional.of(batch));
        when(stocks.findByIdForUpdate(8L)).thenReturn(Optional.of(stock));
        when(batches.findByIdForUpdate(30L)).thenReturn(Optional.of(batch));
        // Idempotency claim succeeds (new key).
        when(jdbc.update(anyString(), any(Object.class), any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(1);
    }

    @Test
    void wasted_removesUnitsFromBatchAndStock_andRecordsAMovement() {
        batch.setExpiryDate(LocalDate.now().plusDays(10));
        when(operations.findById(any())).thenReturn(Optional.of(new BloodInventoryOperation()));
        when(jdbc.update(anyString(), any(Object.class), any(Object.class))).thenReturn(1); // store the result payload

        InventoryOperationResponse result = service.writeOff(
                new WriteOffRequest(30L, WriteOffType.WASTED, 2, "Bag punctured"), "key-w");

        assertEquals(8, result.quantityAfter());
        assertEquals(3, batch.getRemainingQuantity());
        assertEquals(8, stock.getUnitsAvailable());
        ArgumentCaptor<BloodStockMovement> movement = ArgumentCaptor.forClass(BloodStockMovement.class);
        verify(movements).save(movement.capture());
        assertEquals(BloodStockMovementType.WASTED, movement.getValue().getMovementType());
        assertEquals(2, movement.getValue().getUnitsChanged());
    }

    @Test
    void expired_onABatchThatHasNotExpired_isRejected() {
        batch.setExpiryDate(LocalDate.now().plusDays(3));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.writeOff(new WriteOffRequest(30L, WriteOffType.EXPIRED, null, "Expired"), "key-e"));

        assertEquals(409, error.getStatusCode().value());
        assertEquals(5, batch.getRemainingQuantity());
        verify(stocks, never()).save(any());
    }

    @Test
    void wasted_moreThanTheBatchHolds_isRejected_soStockNeverGoesNegative() {
        batch.setExpiryDate(LocalDate.now().plusDays(10));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.writeOff(new WriteOffRequest(30L, WriteOffType.WASTED, 6, "Dropped"), "key-x"));

        assertEquals(400, error.getStatusCode().value());
        verify(stocks, never()).save(any());
    }
}
