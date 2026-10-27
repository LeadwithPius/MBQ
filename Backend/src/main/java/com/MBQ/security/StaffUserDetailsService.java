package com.MBQ.security;

import com.MBQ.model.StaffUser;
import com.MBQ.repository.StaffUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffUserDetailsService implements UserDetailsService {

    private final StaffUserRepository staffUserRepository;

    public StaffUserDetailsService(
            StaffUserRepository staffUserRepository) {
        this.staffUserRepository = staffUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {

        StaffUser staff = staffUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Invalid username or password"));

        return User.withUsername(staff.getUsername())
                .password(staff.getPasswordHash())
                .roles(staff.getRole().name())
                .disabled(!staff.isActive())
                .build();
    }
}