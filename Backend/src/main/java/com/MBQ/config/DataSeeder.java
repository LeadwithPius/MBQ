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
            createDepartment(departments, facility, "Emergency");
            createDepartment(departments, facility, "Maternity");

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

            for (BloodGroup bloodGroup : BloodGroup.values()) {
                BloodStock bloodStock = new BloodStock();
                bloodStock.setFacility(facility);
                bloodStock.setBloodGroup(bloodGroup);
                bloodStock.setUnitsAvailable(10);
                stock.save(bloodStock);
            }
        };
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
