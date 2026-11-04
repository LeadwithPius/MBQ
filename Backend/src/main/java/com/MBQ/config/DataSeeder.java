package com.MBQ.config;

import com.MBQ.model.*;
import com.MBQ.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Development demo data seeder.
 * The demo password must never be used for production accounts.
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(
            FacilityRepository facilities,
            DepartmentRepository departments,
            StaffUserRepository users,
            BloodStockRepository stock,
            BloodStockBatchRepository batches,
            BloodStockMovementRepository movements,
            DonorRepository donors,
            PasswordEncoder encoder) {

        return args -> {
            if (facilities.count() > 0) {
                return;
            }

            Facility facility = new Facility();
            facility.setName("Demo District Hospital");
            facility.setLocation("Lusaka");
            facilities.save(facility);

            Department opd = createDepartment(departments, facility, "OPD");
            Department emergency = createDepartment(departments, facility, "Emergency");
            Department maternity = createDepartment(departments, facility, "Maternity");

            for (Role role : Role.values()) {
                StaffUser user = new StaffUser();
                user.setName(role.name());
                user.setUsername(role.name().toLowerCase());
                user.setPasswordHash(encoder.encode("Password123!"));
                user.setRole(role);

                // Administrators are intentionally not tied to a clinical department.
                user.setDepartment(role == Role.ADMIN ? null : opd);
                users.save(user);
            }

            // Clinical staff for the other departments, so their queues can be demonstrated too.
            // Usernames: emergency.doctor, emergency.nurse, maternity.receptionist, ...
            for (Department department : new Department[] {emergency, maternity}) {
                for (Role role : new Role[] {Role.DOCTOR, Role.NURSE, Role.RECEPTIONIST}) {
                    String prefix = department.getName().toLowerCase();
                    StaffUser user = new StaffUser();
                    user.setName(department.getName() + " " + role.name());
                    user.setUsername(prefix + "." + role.name().toLowerCase());
                    user.setPasswordHash(encoder.encode("Password123!"));
                    user.setRole(role);
                    user.setDepartment(department);
                    users.save(user);
                }
            }

            // Demo blood stock. Every unit sits in a VERIFIED batch with an expiry date,
            // so it can really be issued (FEFO), and the ledger shows how it arrived.
            // O_NEG and AB_NEG start below the low-stock threshold (5) to demonstrate FR6.
            java.time.LocalDate today = java.time.LocalDate.now();
            for (BloodGroup bloodGroup : BloodGroup.values()) {
                int units = switch (bloodGroup) {
                    case O_NEG -> 2;
                    case AB_NEG -> 3;
                    case O_POS -> 15;
                    case A_POS -> 12;
                    default -> 8;
                };

                BloodStock bloodStock = new BloodStock();
                bloodStock.setFacility(facility);
                bloodStock.setBloodGroup(bloodGroup);
                bloodStock.setUnitsAvailable(units);
                stock.save(bloodStock);

                if (bloodGroup == BloodGroup.O_POS) {
                    // Two batches: one expiring in 4 days shows "expiring soon" and FEFO order.
                    seedBatch(batches, movements, bloodStock, "DEMO-O_POS-001", today.plusDays(4), 6, 0);
                    seedBatch(batches, movements, bloodStock, "DEMO-O_POS-002", today.plusDays(30), 9, 6);
                } else {
                    seedBatch(batches, movements, bloodStock, "DEMO-" + bloodGroup + "-001", today.plusDays(30), units, 0);
                }
            }

            seedDonor(donors, "Mwila Chanda", BloodGroup.O_NEG, "0971000001", true);
            seedDonor(donors, "Bupe Mulenga", BloodGroup.O_NEG, "0971000002", true);
            seedDonor(donors, "Kasonde Phiri", BloodGroup.AB_NEG, "0971000003", true);
            seedDonor(donors, "Natasha Banda", BloodGroup.O_POS, "0971000004", true);
            seedDonor(donors, "Joseph Tembo", BloodGroup.A_POS, "0971000005", true);
            seedDonor(donors, "Chilufya Zulu", BloodGroup.O_NEG, "0971000006", false);
        };
    }

    private void seedBatch(
            BloodStockBatchRepository batches,
            BloodStockMovementRepository movements,
            BloodStock stock,
            String reference,
            java.time.LocalDate expiry,
            int units,
            int balanceBefore) {
        BloodStockBatch batch = new BloodStockBatch();
        batch.setStock(stock);
        batch.setBatchReference(reference);
        batch.setExpiryDate(expiry);
        batch.setOpeningQuantity(units);
        batch.setRemainingQuantity(units);
        batch.setVerificationStatus(BloodBatchVerificationStatus.VERIFIED);
        batch.setSourceReference("Demo seed data");
        batches.save(batch);

        BloodStockMovement movement = new BloodStockMovement();
        movement.setStock(stock);
        movement.setBatch(batch);
        movement.setMovementType(BloodStockMovementType.RECEIVED);
        movement.setUnitsChanged(units);
        movement.setQuantityBefore(balanceBefore);
        movement.setQuantityAfter(balanceBefore + units);
        movement.setReason("Demo opening stock");
        movement.setCreatedAt(java.time.LocalDateTime.now());
        movements.save(movement);
    }

    private void seedDonor(DonorRepository donors, String name, BloodGroup group,
                           String contact, boolean eligible) {
        Donor donor = new Donor();
        donor.setName(name);
        donor.setBloodGroup(group);
        donor.setContact(contact);
        donor.setEligible(eligible);
        donors.save(donor);
    }

    private Department createDepartment(
            DepartmentRepository repository,
            Facility facility,
            String name) {
        Department department = new Department();
        department.setName(name);
        department.setFacility(facility);
        return repository.save(department);
    }
}
