package com.datingapp.service;

import com.datingapp.dto.AuthResponse;
import com.datingapp.dto.LoginRequest;
import com.datingapp.dto.RegisterRequest;
import com.datingapp.dto.UserProfileResponse;
import com.datingapp.entity.User;
import com.datingapp.exception.ConflictException;
import com.datingapp.repository.UserRepository;
import com.datingapp.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Compared against when the email is unknown so response timing doesn't reveal registered emails. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       UserService userService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-equalization-dummy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(UserService.normalizeEmail(req.email()))) {
            throw new ConflictException("Email is already registered");
        }
        User user = userService.createAccount(req, passwordEncoder.encode(req.password()));
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        Optional<User> user = userRepository.findByEmail(UserService.normalizeEmail(req.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(req.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return tokenFor(user.get());
    }

    private AuthResponse tokenFor(User user) {
        return AuthResponse.bearer(jwtService.issueToken(user.getId()),
                jwtService.getTtl().toSeconds(), UserProfileResponse.from(user));
    }
}
