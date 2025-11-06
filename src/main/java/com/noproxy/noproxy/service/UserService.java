package com.noproxy.noproxy.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.noproxy.noproxy.model.Role;
import com.noproxy.noproxy.repository.UserRepository;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ✅ Load user for Spring Security
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        com.noproxy.noproxy.model.User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().name())
                .build();
    }

    // ✅ Register new user (with imagePath for STUDENT role)
    public com.noproxy.noproxy.model.User registerUser(
            String name, String email, String password, Role role, String imagePath) {

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered!");
        }

        com.noproxy.noproxy.model.User newUser = new com.noproxy.noproxy.model.User();
        newUser.setName(name);
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setRole(role);

        if (role == Role.STUDENT && imagePath != null && !imagePath.isEmpty()) {
            newUser.setImagePath(imagePath);
        }

        return userRepository.save(newUser);
    }

    // ✅ Fetch user by email
    public Optional<com.noproxy.noproxy.model.User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
