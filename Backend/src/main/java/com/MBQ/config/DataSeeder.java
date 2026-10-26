package com.MBQ.config;

import com.MBQ.model.*;
import com.MBQ.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Seeds one facility, departments, a user per role and stock rows. Runs only on an empty DB. */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(FacilityRepository facilities, DepartmentRepository departments,
                           StaffUserRepository users, BloodStockRepository stock,
                           PasswordEncoder encoder) {
        return args -> {
            if (facilities.count() > 0) return;

            Facility f = new Facility();
            f.setName("Demo District Hospital");
            f.setLocation("Lusaka");
            facilities.save(f);

            Department opd = dept(departments, f, "OPD");
            dept(departments, f, "Emergency");
            dept(departments, f, "Maternity");

            for (Role r : Role.values()) {
                StaffUser u = new StaffUser();
                u.setName(r.name());
                u.setUsername(r.name().toLowerCase());
                u.setPasswordHash(encoder.encode("Password123!"));   // dev only
                u.setRole(r);
                u.setDepartment(opd);
                users.save(u);
            }

            for (BloodGroup g : BloodGroup.values()) {
                BloodStock s = new BloodStock();
                s.setFacility(f);
                s.setBloodGroup(g);
                s.setUnitsAvailable(10);
                stock.save(s);
            }
        };
    }

    private Department dept(DepartmentRepository repo, Facility f, String name) {
        Department d = new Department();
        d.setName(name);
        d.setFacility(f);
        return repo.save(d);
    }
}
