package com.MBQ.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.MBQ.model.BloodGroup;

class BloodCompatibilityTest {

    @Test
    void oNegativeIsUniversalDonor_abPositiveIsUniversalRecipient() {
        for (BloodGroup recipient : BloodGroup.values()) {
            assertTrue(BloodCompatibility.canReceive(recipient, BloodGroup.O_NEG), recipient + " <- O-");
        }
        for (BloodGroup donor : BloodGroup.values()) {
            assertTrue(BloodCompatibility.canReceive(BloodGroup.AB_POS, donor), "AB+ <- " + donor);
        }
    }

    @Test
    void rhNegativeRecipientCannotReceiveRhPositive_andAboMismatchIsRejected() {
        assertFalse(BloodCompatibility.canReceive(BloodGroup.A_NEG, BloodGroup.A_POS));
        assertFalse(BloodCompatibility.canReceive(BloodGroup.O_POS, BloodGroup.A_POS));
        assertFalse(BloodCompatibility.canReceive(BloodGroup.A_POS, BloodGroup.B_POS));
        assertFalse(BloodCompatibility.canReceive(BloodGroup.B_NEG, BloodGroup.AB_NEG));
        assertTrue(BloodCompatibility.canReceive(BloodGroup.B_POS, BloodGroup.O_POS));
        assertTrue(BloodCompatibility.canReceive(BloodGroup.AB_NEG, BloodGroup.B_NEG));
    }

    @Test
    void unknownRecipientGroup_onlyAllowsONegative() {
        assertTrue(BloodCompatibility.canReceive(null, BloodGroup.O_NEG));
        assertFalse(BloodCompatibility.canReceive(null, BloodGroup.O_POS));
        assertEquals(1, BloodCompatibility.compatibleDonorsFor(null).size());
    }
}
