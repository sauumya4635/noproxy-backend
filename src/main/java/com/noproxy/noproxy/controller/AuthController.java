package com.noproxy.noproxy.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.noproxy.noproxy.DTO.AuthRequest;
import com.noproxy.noproxy.DTO.AuthResponse;
import com.noproxy.noproxy.DTO.RegisterRequest;
import com.noproxy.noproxy.DTO.RegisterResponse;
import com.noproxy.noproxy.model.Role;
import com.noproxy.noproxy.model.User;
import com.noproxy.noproxy.repository.UserRepository;
import com.noproxy.noproxy.service.UserService;
import com.noproxy.noproxy.util.JwtUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private static final String UPLOAD_DIR = "uploads";

    public AuthController(AuthenticationManager authenticationManager, UserService userService,
                          UserRepository userRepository, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    // ✅ Ping endpoint
    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        return ResponseEntity.ok(Map.of("status", "Java backend running!", "port", "5501"));
    }

    // ✅ Register user (clean error handling)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest body) {
        try {
            Role role = Role.valueOf(body.getRole().toUpperCase());

            User user = userService.registerUser(
                    body.getId(),
                    body.getName(),
                    body.getEmail(),
                    body.getPassword(),
                    role,
                    body.getImagePath()
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

        } catch (IllegalArgumentException e) {
            // Invalid role string
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid role specified."));

        } catch (DataIntegrityViolationException e) {
            // Duplicate email or DB constraint violation
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "User already exists or invalid data."));

        } catch (RuntimeException e) {
            // General fallback (any other unchecked exception)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error: " + e.getMessage()));
        }
    }

    // ✅ Login with proper multi-catch
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest loginRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            UserDetails userDetails = userService.loadUserByUsername(loginRequest.getEmail());
            User user = userService.getUserByEmail(loginRequest.getEmail()).orElseThrow();

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
                    user.getRole() == Role.STUDENT ? user.getImagePath() : null
            );

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password."));

        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid login request: " + e.getMessage()));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error during login: " + e.getMessage()));
        }
    }

    // ✅ Upload student photo (multi-catch for IO-related issues)
    @PostMapping("/upload-photo")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file,
                                         @RequestParam("email") String email) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found."));
            }

            Files.createDirectories(Paths.get(UPLOAD_DIR));

            String cleanName = Objects.requireNonNull(file.getOriginalFilename())
                    .replaceAll("[^a-zA-Z0-9._-]", "_");

            Path filePath = Paths.get(UPLOAD_DIR, cleanName).toAbsolutePath();
            file.transferTo(filePath);

            User user = userOpt.get();
            user.setImagePath(filePath.toString());
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Photo uploaded successfully.",
                    "path", filePath.toString()
            ));

        } catch (IOException | IllegalStateException | SecurityException e) {
            // ✅ Multi-catch block handles all file system errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: " + e.getMessage()));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error: " + e.getMessage()));
        }
    }
}
