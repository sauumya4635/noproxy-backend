package com.noproxy.noproxy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.noproxy.noproxy.DTO.AuthRequest;
import com.noproxy.noproxy.DTO.AuthResponse;
import com.noproxy.noproxy.DTO.RegisterRequest;
import com.noproxy.noproxy.DTO.RegisterResponse;
import com.noproxy.noproxy.model.Role;
import com.noproxy.noproxy.model.User;
import com.noproxy.noproxy.service.UserService;
import com.noproxy.noproxy.util.JwtUtil;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, UserService userService, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // ✅ Ping endpoint (for testing connectivity)
    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of(
            "status", "Java backend running!",
            "port", "8080"
        ));
    }

    // ✅ Register a new user
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body) {
        try {
            Role role = Role.valueOf(body.getRole().toUpperCase());

            User user = userService.registerUser(
                    body.getName(),
                    body.getEmail(),
                    body.getPassword(),
                    role,
                    body.getImagePath() // only for students
            );

            RegisterResponse response = new RegisterResponse(
                    "User registered successfully!",
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getRole() == Role.STUDENT ? user.getImagePath() : null
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // ✅ Login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest loginRequest) {
        String email = loginRequest.getEmail();
        String password = loginRequest.getPassword();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }

        UserDetails userDetails = userService.loadUserByUsername(email);
        User user = userService.getUserByEmail(email).get();

        String token = jwtUtil.generateToken(
                userDetails,
                user.getRole().name(),
                user.getId().toString(),
                user.getName()
        );

        AuthResponse response = new AuthResponse(
                token,
                user.getRole().name(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name().equals("STUDENT") ? user.getImagePath() : null
        );

        return ResponseEntity.ok(response);
    }
}
