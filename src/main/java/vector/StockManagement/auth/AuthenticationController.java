package vector.StockManagement.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vector.StockManagement.model.Token;
import vector.StockManagement.model.User;
import vector.StockManagement.repositories.TokenRepository;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final TokenRepository tokenRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(@RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    @PostMapping("/refresh-token")
    public void refresh(HttpServletRequest request, HttpServletResponse response) throws IOException {
        authenticationService.refreshToken(request, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokenRepository.findByToken(header.substring(7)).ifPresent((Token t) -> {
                t.setExpired(true);
                t.setRevoked(true);
                tokenRepository.save(t);
            });
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal User user,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(user, request);
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    @PostMapping({"/forgot-password", "/request-password-reset"})
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authenticationService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If this email has an account, a reset link has been sent"));
    }

    @GetMapping("/reset-password")
    public ResponseEntity<Map<String, String>> verifyReset(@RequestParam("token") String token) {
        authenticationService.verifyResetToken(token);
        return ResponseEntity.ok(Map.of("message", "The link is valid"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request.getResetToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset"));
    }
}
