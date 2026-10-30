package vector.StockManagement.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vector.StockManagement.model.User;
import vector.StockManagement.model.enums.Role;
import vector.StockManagement.model.enums.UserStatus;
import vector.StockManagement.repositories.UserRepository;

import java.time.LocalDateTime;

/**
 * There is no public sign-up. On an empty database the platform operator is created from
 * SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD, and restaurants are created from the admin console after that.
 */
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.super-admin-email:}")
    private String email;

    @Value("${app.bootstrap.super-admin-password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) return;
        if (email.isBlank() || password.length() < 8) {
            logger.warn("No super admin exists. Set SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD (8+ characters) and restart.");
            return;
        }
        userRepository.save(User.builder()
                .firstName("Platform")
                .lastName("Admin")
                .email(email.trim().toLowerCase())
                .password(passwordEncoder.encode(password))
                .role(Role.SUPER_ADMIN)
                .userStatus(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        logger.info("Created the super admin account for {}", email);
    }
}
