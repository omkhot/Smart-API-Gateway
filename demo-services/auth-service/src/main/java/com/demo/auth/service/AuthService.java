package com.demo.auth.service;

import com.demo.auth.dto.AuthResponse;
import com.demo.auth.dto.LoginRequest;
import com.demo.auth.dto.RegisterRequest;
import com.demo.auth.exception.InvalidCredentialsException;
import com.demo.auth.exception.UserAlreadyExistsException;
import com.demo.auth.model.AuthUser;
import com.demo.auth.repository.AuthUserRepository;
import com.demo.auth.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AuthUserRepository authUserRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider jwtTokenProvider) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void register(RegisterRequest request) {
        if (authUserRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username already taken: " + request.getUsername());
        }
        String hash = passwordEncoder.encode(request.getPassword());
        authUserRepository.save(new AuthUser(request.getUsername(), hash, DEFAULT_ROLE));
    }

    public AuthResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, jwtTokenProvider.getExpirationSeconds(), user.getUsername());
    }
}
