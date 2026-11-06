package com.MBQ.service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.MBQ.model.BloodGroup;

/**
 * Standard ABO/RhD red-cell compatibility: which donor groups a recipient may
 * receive. If the recipient's group is unknown, only O negative is safe.
 */
public final class BloodCompatibility {

    private static final Map<BloodGroup, Set<BloodGroup>> CAN_RECEIVE = new EnumMap<>(BloodGroup.class);

    static {
        CAN_RECEIVE.put(BloodGroup.O_NEG, EnumSet.of(BloodGroup.O_NEG));
        CAN_RECEIVE.put(BloodGroup.O_POS, EnumSet.of(BloodGroup.O_POS, BloodGroup.O_NEG));
        CAN_RECEIVE.put(BloodGroup.A_NEG, EnumSet.of(BloodGroup.A_NEG, BloodGroup.O_NEG));
        CAN_RECEIVE.put(BloodGroup.A_POS, EnumSet.of(BloodGroup.A_POS, BloodGroup.A_NEG, BloodGroup.O_POS, BloodGroup.O_NEG));
        CAN_RECEIVE.put(BloodGroup.B_NEG, EnumSet.of(BloodGroup.B_NEG, BloodGroup.O_NEG));
        CAN_RECEIVE.put(BloodGroup.B_POS, EnumSet.of(BloodGroup.B_POS, BloodGroup.B_NEG, BloodGroup.O_POS, BloodGroup.O_NEG));
        CAN_RECEIVE.put(BloodGroup.AB_NEG, EnumSet.of(BloodGroup.AB_NEG, BloodGroup.A_NEG, BloodGroup.B_NEG, BloodGroup.O_NEG));
        CAN_RECEIVE.put(BloodGroup.AB_POS, EnumSet.allOf(BloodGroup.class));
    }

    private BloodCompatibility() {}

    public static boolean canReceive(BloodGroup recipient, BloodGroup donor) {
        if (donor == null) return false;
        if (recipient == null) return donor == BloodGroup.O_NEG;
        return CAN_RECEIVE.get(recipient).contains(donor);
    }

    public static Set<BloodGroup> compatibleDonorsFor(BloodGroup recipient) {
        return recipient == null ? EnumSet.of(BloodGroup.O_NEG) : EnumSet.copyOf(CAN_RECEIVE.get(recipient));
    }
}
