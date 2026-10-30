package vector.StockManagement.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vector.StockManagement.config.JwtService;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.Token;
import vector.StockManagement.model.User;
import vector.StockManagement.model.enums.TokenType;
import vector.StockManagement.repositories.TenantRepository;
import vector.StockManagement.repositories.TokenRepository;
import vector.StockManagement.repositories.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRepository tokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final JavaMailSender mailSender;
    private final LoginThrottle loginThrottle;

    @Value("${spring.mail.from:}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            throw ApiException.badRequest("Enter your email and password");
        }
        String email = request.getEmail().trim().toLowerCase();
        String throttleKey = email + "|" + clientAddress();
        loginThrottle.check(throttleKey);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            loginThrottle.failed(throttleKey);
            throw e;
        }
        loginThrottle.succeeded(throttleKey);
        User user = userRepository.findByEmail(email).orElseThrow(() -> ApiException.badRequest("Invalid email or password"));
        if (user.getTenant() != null && !Boolean.TRUE.equals(tenantRepository.isActive(user.getTenant().getId()))) {
            throw ApiException.forbidden("This restaurant account is suspended. Contact the platform administrator.");
        }
        String token = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user, refreshToken);
        saveUserToken(user, token);
        return AuthenticationResponse.builder().accessToken(token).refreshToken(refreshToken).build();
    }

    private String clientAddress() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            return sra.getRequest().getRemoteAddr();
        }
        return "unknown";
    }

    @Transactional
    public void changePassword(User principal, ChangePasswordRequest request) {
        User user = userRepository.findById(principal.getId()).orElseThrow();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("Your current password is not correct");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private void revokeAllUserTokens(User user) {
        var validTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (validTokens.isEmpty()) return;
        validTokens.forEach(token -> {
            token.setRevoked(true);
            token.setExpired(true);
        });
        tokenRepository.saveAll(validTokens);
    }

    private void saveUserToken(User user, String jwtToken) {
        tokenRepository.save(Token.builder()
                .token(jwtToken)
                .user(user)
                .tokenType(TokenType.BEARER)
                .expired(false)
                .revoked(false)
                .createdDate(LocalDateTime.now())
                .build());
    }

    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }
        String refreshToken = authHeader.substring(7);
        String email = jwtService.extractUsername(refreshToken);
        if (email == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid refresh token");
            return;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isEnabled() || !jwtService.isTokenValid(refreshToken, user)
                || (user.getTenant() != null && !Boolean.TRUE.equals(tenantRepository.isActive(user.getTenant().getId())))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired refresh token");
            return;
        }
        String accessToken = jwtService.generateToken(user);
        saveUserToken(user, accessToken);
        response.setContentType("application/json");
        new ObjectMapper().writeValue(response.getOutputStream(),
                AuthenticationResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build());
    }

    @Transactional
    public void requestPasswordReset(String email) {
        // Same answer whether or not the address exists, so the form cannot be used to find accounts.
        User user = userRepository.findByEmail(email == null ? "" : email.trim().toLowerCase()).orElse(null);
        if (user == null) return;
        String resetToken = UUID.randomUUID().toString();
        tokenRepository.save(Token.builder()
                .token(resetToken)
                .user(user)
                .tokenType(TokenType.PASSWORD_RESET)
                .expired(false)
                .revoked(false)
                .createdDate(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());
        String link = frontendUrl + "/auth/reset-password?token=" + resetToken;
        sendEmail("Reset your SALES password",
                "<p>Hello " + user.getFirstName() + ",</p><p>Use this link to choose a new password. It expires in one hour.</p>"
                        + "<p><a href=\"" + link + "\">Reset password</a></p>"
                        + "<p>If you did not ask for this, ignore this email.</p>", user.getEmail());
    }

    @Transactional(readOnly = true)
    public void verifyResetToken(String resetToken) {
        Token token = tokenRepository.findByToken(resetToken).orElseThrow(() -> ApiException.badRequest("Invalid reset link"));
        validateResetToken(token);
    }

    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw ApiException.badRequest("Password must be at least 8 characters");
        }
        Token token = tokenRepository.findByToken(resetToken).orElseThrow(() -> ApiException.badRequest("Invalid reset link"));
        validateResetToken(token);
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        token.setExpired(true);
        token.setRevoked(true);
        tokenRepository.save(token);
        revokeAllUserTokens(user);
    }

    private void validateResetToken(Token token) {
        if (token.isExpired() || token.isRevoked() || token.getTokenType() != TokenType.PASSWORD_RESET
                || token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("This reset link is invalid or has expired");
        }
    }

    private void sendEmail(String subject, String htmlBody, String toEmail) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            if (fromEmail != null && !fromEmail.isBlank()) helper.setFrom(fromEmail.replace("\"", ""));
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException | RuntimeException e) {
            logger.error("Could not send email to {}", toEmail, e);
        }
    }
}
