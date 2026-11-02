package com.MBQ.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@SpringBootTest
class PasswordVerificationTest {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    void authenticateAdminSuccessfully() {

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                "admin",
                                "Password123!"
                        )
                );

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());

        System.out.println("Authentication successful: "
                + authentication.isAuthenticated());

        System.out.println("Authenticated username: "
                + authentication.getName());

        System.out.println("Authorities: "
                + authentication.getAuthorities());
    }
}
