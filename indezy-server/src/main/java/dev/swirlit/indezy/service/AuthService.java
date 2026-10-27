package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.exception.AccountAlreadyExistsException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.TwoFactorRequiredException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.SignInMethod;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.util.SecurityUtils;
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
    private final AccountSessionService sessions;
    private final AccountEventService events;
    private final JwtDecoder keycloakJwtDecoder;
    private final TotpService totpService;
    private final JobBoardSources jobBoardSources;

    public LoginResponse login(LoginRequest request) {
        log.debug("Attempting login");
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid email or password"));

        // Deleted and disabled accounts cannot log in; report the same generic error to avoid leaking state.
        if (!user.isActive()) {
            throw new ResourceNotFoundException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            events.record(user.getId(), AccountEventType.SIGN_IN_REFUSED, "PASSWORD");
            throw new ResourceNotFoundException("Invalid email or password");
        }
        requireTwoFactorCode(user, request.getTotpCode());

        Freelance freelance = ensureFreelanceProfile(user);
        String token = sessions.open(user, SignInMethod.PASSWORD);
        
        log.debug("Login successful for user ID: {}", user.getId());
        return toLoginResponse(user, freelance.getId(), token);
    }

    /** Accounts with two-factor authentication enabled must also present a valid TOTP code. */
    private void requireTwoFactorCode(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || user.getTwoFactorSecret() == null) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new TwoFactorRequiredException();
        }
        if (!totpService.validateCode(user.getTwoFactorSecret(), code.trim())) {
            events.record(user.getId(), AccountEventType.SIGN_IN_REFUSED, "TWO_FACTOR");
            throw new ResourceNotFoundException("Invalid email or password");
        }
    }

    public LoginResponse register(RegisterRequest request) {
        log.debug("Attempting registration");
        
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AccountAlreadyExistsException("User with this email already exists");
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setOnboardingPending(true);

        user = userRepository.save(user);

        Freelance freelance = ensureFreelanceProfile(user);
        String token = sessions.open(user, SignInMethod.REGISTRATION);
        
        log.debug("Registration successful for user ID: {}", user.getId());
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
        if (email == null || email.isBlank() || username == null || username.isBlank() ||
                !Boolean.TRUE.equals(emailVerified)) {
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
            created.setOnboardingPending(true);
            return userRepository.save(created);
        });
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid account");
        }

        Freelance freelance = ensureFreelanceProfile(user);
        // The app exchanges the Keycloak identity on every load: the session its token belongs to goes on.
        String token = sessions.resumeOrOpen(user, SignInMethod.KEYCLOAK, SecurityUtils.getCurrentSessionId());
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
            Freelance saved = freelanceRepository.save(created);
            jobBoardSources.seed(saved.getId());
            return saved;
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
        userInfo.setOnboardingPending(user.getOnboardingPending());
        response.setUser(userInfo);
        return response;
    }
}
