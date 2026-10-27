package com.MBQ.controller;

import com.MBQ.dto.AuthDtos;
import com.MBQ.model.StaffUser;
import com.MBQ.repository.StaffUserRepository;
import com.MBQ.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final StaffUserRepository staffUserRepository;
    private final JwtService jwtService;
    private final long expirationMs;

    public AuthController(
            AuthenticationManager authenticationManager,
            StaffUserRepository staffUserRepository,
            JwtService jwtService,
            @Value("${app.jwt.expiration-ms:28800000}")
            long expirationMs) {

        this.authenticationManager = authenticationManager;
        this.staffUserRepository = staffUserRepository;
        this.jwtService = jwtService;
        this.expirationMs = expirationMs;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()));

        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username or password");
        }

        StaffUser staff = staffUserRepository
                .findByUsername(request.username())
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"));

        org.springframework.security.core.userdetails.UserDetails principal =
                org.springframework.security.core.userdetails.User
                        .withUsername(staff.getUsername())
                        .password(staff.getPasswordHash())
                        .roles(staff.getRole().name())
                        .disabled(!staff.isActive())
                        .build();

        String token = jwtService.generateToken(principal);

        Long departmentId = staff.getDepartment() == null
                ? null
                : staff.getDepartment().getId();

        Long facilityId = staff.getDepartment() == null
                || staff.getDepartment().getFacility() == null
                ? null
                : staff.getDepartment().getFacility().getId();

        AuthDtos.StaffProfile profile = new AuthDtos.StaffProfile(
                staff.getId(),
                staff.getName(),
                staff.getUsername(),
                staff.getRole().name(),
                departmentId,
                facilityId);

        return new AuthDtos.LoginResponse(
                token,
                "Bearer",
                expirationMs,
                profile);
    }
}