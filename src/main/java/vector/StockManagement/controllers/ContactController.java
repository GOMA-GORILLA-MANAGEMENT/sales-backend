package vector.StockManagement.controllers;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import vector.StockManagement.exceptions.ApiException;
import vector.StockManagement.model.dto.ContactRequest;
import vector.StockManagement.model.dto.PricingRequest;

import java.util.Map;
import java.util.regex.Pattern;

/** The two public forms on the landing page. Input is checked and escaped before it goes into an email. */
@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {
    private static final Logger logger = LoggerFactory.getLogger(ContactController.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>\"]+@[^\\s@<>\"]+\\.[^\\s@<>\"]+$");

    private final JavaMailSender mailSender;

    @Value("${app.contact.to:ntwaliisimbivieira@gmail.com}")
    private String toEmail;

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> contactRequest(@RequestBody ContactRequest r) {
        String email = clean(r.getEmailAddress(), 254);
        requireEmail(email);
        String name = clean(r.getFullName(), 120);
        String subject = clean(r.getSubject(), 150);
        String message = clean(r.getMessage(), 5000);
        if (name.isEmpty() || message.isEmpty()) throw ApiException.badRequest("Please fill in your name and message");
        send("Website message: " + subject, email,
                "<p>" + esc(message).replace("\n", "<br>") + "</p><p>From: " + esc(name) + " (" + esc(email) + ")</p>");
        return ResponseEntity.ok(Map.of("message", "Email sent"));
    }

    @PostMapping("/pricing")
    public ResponseEntity<Map<String, String>> contactPricing(@RequestBody PricingRequest r) {
        String email = clean(r.getEmail(), 254);
        requireEmail(email);
        String body = "<p>Name: " + esc(clean(r.getFullName(), 120)) + "<br>Business: " + esc(clean(r.getBusinessName(), 150))
                + "<br>Phone: " + esc(clean(r.getPhoneNumber(), 40)) + "<br>Email: " + esc(email)
                + "<br>Business type: " + esc(String.valueOf(r.getBusinessType()))
                + "<br>Plan: " + esc(String.valueOf(r.getPricingType())) + "</p>";
        send("Pricing request", email, body);
        return ResponseEntity.ok(Map.of("message", "Email sent"));
    }

    private void send(String subject, String replyTo, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject.replaceAll("[\\r\\n]", " "));
            helper.setReplyTo(replyTo);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            logger.error("Could not send contact email", e);
        }
    }

    private static void requireEmail(String email) {
        if (!EMAIL.matcher(email).matches()) throw ApiException.badRequest("Enter a valid email address");
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
