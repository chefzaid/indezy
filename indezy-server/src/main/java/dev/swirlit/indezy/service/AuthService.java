package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final FreelanceRepository freelanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtDecoder keycloakJwtDecoder;

    public LoginResponse login(LoginRequest request) {
        log.debug("Attempting login for email: {}", request.getEmail());
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        // Soft-deleted accounts cannot log in; report the same generic error to avoid leaking state.
        if (user.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        Freelance freelance = ensureFreelanceProfile(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        
        log.debug("Login successful for user: {}", user.getEmail());
        return toLoginResponse(user, freelance.getId(), token);
    }

    public LoginResponse register(RegisterRequest request) {
        log.debug("Attempting registration for email: {}", request.getEmail());
        
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        user = userRepository.save(user);

        Freelance freelance = ensureFreelanceProfile(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        
        log.debug("Registration successful for user: {}", user.getEmail());
        return toLoginResponse(user, freelance.getId(), token);
    }

    public LoginResponse loginWithKeycloak(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadCredentialsException("Keycloak access token is missing");
        }

        Jwt identity = keycloakJwtDecoder.decode(accessToken);
        String email = identity.getClaimAsString("email");
        String username = identity.getClaimAsString("preferred_username");
        Boolean emailVerified = identity.getClaimAsBoolean("email_verified");
        if (email == null || email.isBlank() || username == null || username.isBlank()
                || !Boolean.TRUE.equals(emailVerified)) {
            throw new BadCredentialsException("Keycloak identity is incomplete or unverified");
        }

        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail).orElseGet(() -> {
            User created = new User();
            String givenName = identity.getClaimAsString("given_name");
            String familyName = identity.getClaimAsString("family_name");
            created.setFirstName(valueOrFallback(givenName, username));
            created.setLastName(valueOrFallback(familyName, "User"));
            created.setEmail(normalizedEmail);
            created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            return userRepository.save(created);
        });
        if (user.getDeletedAt() != null) {
            throw new BadCredentialsException("Invalid account");
        }

        Freelance freelance = ensureFreelanceProfile(user);
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        return toLoginResponse(user, freelance.getId(), token);
    }

    private static String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Freelance ensureFreelanceProfile(User user) {
        return freelanceRepository.findByEmail(user.getEmail()).orElseGet(() -> {
            Freelance created = new Freelance();
            created.setFirstName(user.getFirstName());
            created.setLastName(user.getLastName());
            created.setEmail(user.getEmail());
            created.setStatus(EmploymentStatus.FREELANCE);
            return freelanceRepository.save(created);
        });
    }

    private static LoginResponse toLoginResponse(User user, Long freelanceId, String token) {
        LoginResponse response = new LoginResponse();
        response.setToken(token);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        // The web application has historically used user.id as its freelance
        // profile identifier. Keep that public contract and expose the
        // authentication-account identifier separately.
        userInfo.setId(freelanceId);
        userInfo.setAccountId(user.getId());
        userInfo.setEmail(user.getEmail());
        userInfo.setFirstName(user.getFirstName());
        userInfo.setLastName(user.getLastName());
        response.setUser(userInfo);
        return response;
    }
}
