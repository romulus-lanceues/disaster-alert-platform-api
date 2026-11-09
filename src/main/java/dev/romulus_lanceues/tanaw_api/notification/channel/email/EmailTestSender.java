package dev.romulus_lanceues.tanaw_api.notification.channel.email;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.ChannelVerifier;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Handles verification of email endpoints by sending a test multipart email
 * or logging the action when dry-run mode is enabled.
 */
@Component
@Slf4j
public class EmailTestSender implements ChannelVerifier {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final EmailDestinationValidator validator;
    private final DestinationMasker masker;

    public EmailTestSender(
            JavaMailSender mailSender,
            NotificationProperties properties,
            EmailDestinationValidator validator,
            DestinationMasker masker
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.validator = validator;
        this.masker = masker;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void verify(String destination) {
        validator.validate(destination);

        if (Boolean.TRUE.equals(properties.dryRun())) {
            log.info("Dry-run mode enabled: skipping outbound Email test message to {}",
                    masker.mask(NotificationChannel.EMAIL, destination));
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            String fromAddress = (properties.email() != null && properties.email().fromAddress() != null)
                    ? properties.email().fromAddress()
                    : "no-reply@tanaw.dev";
            String fromName = (properties.email() != null && properties.email().fromName() != null)
                    ? properties.email().fromName()
                    : "Tanaw Alerts";

            helper.setFrom(fromAddress, fromName);
            helper.setTo(destination);
            helper.setSubject("[TEST] Tanaw Alert Notification — Endpoint Verification");

            String textContent = "This is a test notification confirming that your email endpoint is active and configured correctly.";
            String htmlContent = "<p>This is a test notification confirming that your email endpoint is active and configured correctly.</p>";
            helper.setText(textContent, htmlContent);

            mimeMessage.saveChanges();
            mailSender.send(mimeMessage);

            log.info("Successfully sent test verification email to {}",
                    masker.mask(NotificationChannel.EMAIL, destination));
        } catch (MailAuthenticationException ex) {
            log.warn("Email verification authentication failed for destination {}",
                    masker.mask(NotificationChannel.EMAIL, destination));
            throw new EndpointVerificationException(
                    "Email verification failed: mail server authentication error"
            );
        } catch (MailSendException ex) {
            log.warn("Email verification send failed for destination {}",
                    masker.mask(NotificationChannel.EMAIL, destination));
            throw new EndpointVerificationException(
                    "Email verification failed: unable to send email"
            );
        } catch (MailException ex) {
            log.warn("Email verification mail exception for destination {}: {}",
                    masker.mask(NotificationChannel.EMAIL, destination), ex.getClass().getSimpleName());
            throw new EndpointVerificationException(
                "Email verification failed: mail service error"
            );
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.warn("Email verification message preparation error for destination {}: {}",
                    masker.mask(NotificationChannel.EMAIL, destination), ex.getClass().getSimpleName());
            throw new EndpointVerificationException(
                    "Email verification failed: message preparation error"
            );
        } catch (EndpointVerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Email verification unexpected error for destination {}: {}",
                    masker.mask(NotificationChannel.EMAIL, destination), ex.getClass().getSimpleName());
            throw new EndpointVerificationException(
                    "Email verification failed: unexpected error occurred"
            );
        }
    }
}
