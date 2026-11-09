package dev.romulus_lanceues.tanaw_api.notification.channel.email;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.EndpointVerificationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailTestSender Unit Tests")
class EmailTestSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailDestinationValidator validator;

    private DestinationMasker masker;
    private EmailTestSender testSender;

    private NotificationProperties propertiesDryRun;
    private NotificationProperties propertiesLive;

    private final String validEmail = "recipient@example.com";

    @BeforeEach
    void setUp() {
        masker = new DestinationMasker();

        propertiesDryRun = new NotificationProperties(
                true,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="),
                null,
                new NotificationProperties.Email("alerts@tanaw.dev", "Tanaw Test System", Duration.ofSeconds(5), Duration.ofSeconds(10))
        );

        propertiesLive = new NotificationProperties(
                false,
                new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                new NotificationProperties.Fanout(Duration.ofHours(6)),
                new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                new NotificationProperties.Crypto("dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="),
                null,
                new NotificationProperties.Email("alerts@tanaw.dev", "Tanaw Alerts", Duration.ofSeconds(5), Duration.ofSeconds(10))
        );
    }

    @Test
    @DisplayName("channel returns NotificationChannel.EMAIL")
    void channelReturnsEmail() {
        testSender = new EmailTestSender(mailSender, propertiesDryRun, validator, masker);
        assertThat(testSender.channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Nested
    @DisplayName("Dry-Run Mode")
    class DryRunModeTests {

        @Test
        @DisplayName("validates destination and skips sending email when dry-run is true")
        void skipsSendInDryRun() {
            testSender = new EmailTestSender(mailSender, propertiesDryRun, validator, masker);
            willDoNothing().given(validator).validate(validEmail);

            testSender.verify(validEmail);

            then(validator).should().validate(validEmail);
            then(mailSender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("throws InvalidDestinationException when email syntax is invalid in dry-run mode")
        void throwsOnInvalidDestinationInDryRun() {
            testSender = new EmailTestSender(mailSender, propertiesDryRun, validator, masker);
            willThrow(new InvalidDestinationException("Invalid email format"))
                    .given(validator).validate("invalid-email");

            assertThatThrownBy(() -> testSender.verify("invalid-email"))
                    .isInstanceOf(InvalidDestinationException.class)
                    .hasMessageContaining("Invalid email format");

            then(mailSender).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Live Send Mode")
    class LiveSendModeTests {

        @BeforeEach
        void initLiveSender() {
            testSender = new EmailTestSender(mailSender, propertiesLive, validator, masker);
        }

        @Test
        @DisplayName("sends multipart test email with correct headers and content")
        void sendsMultipartTestEmail() throws MessagingException, IOException {
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            willDoNothing().given(mailSender).send(any(MimeMessage.class));

            testSender.verify(validEmail);

            then(validator).should().validate(validEmail);
            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            then(mailSender).should().send(captor.capture());

            MimeMessage sentMessage = captor.getValue();
            assertThat(sentMessage.getSubject()).isEqualTo("[TEST] Tanaw Alert Notification — Endpoint Verification");

            Address[] from = sentMessage.getFrom();
            assertThat(from).hasSize(1);
            InternetAddress fromAddress = (InternetAddress) from[0];
            assertThat(fromAddress.getAddress()).isEqualTo("alerts@tanaw.dev");
            assertThat(fromAddress.getPersonal()).isEqualTo("Tanaw Alerts");

            Address[] recipients = sentMessage.getRecipients(Message.RecipientType.TO);
            assertThat(recipients).hasSize(1);
            assertThat(((InternetAddress) recipients[0]).getAddress()).isEqualTo(validEmail);

            // Verify multipart email content (text/plain + text/html)
            assertThat(sentMessage.getContentType()).startsWith("multipart/");
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            sentMessage.writeTo(baos);
            String rawMessage = baos.toString(java.nio.charset.StandardCharsets.UTF_8);

            assertThat(rawMessage).contains("text/plain");
            assertThat(rawMessage).contains("text/html");
            assertThat(rawMessage).contains("This is a test notification confirming that your email endpoint is active and configured correctly.");
            assertThat(rawMessage).contains("<p>This is a test notification confirming that your email endpoint is active and configured correctly.</p>");
        }

        @Test
        @DisplayName("mail authentication failure maps to EndpointVerificationException without credentials leak")
        void authFailureThrowsEndpointVerificationException() {
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            willThrow(new MailAuthenticationException("535 5.7.8 Authentication credentials invalid"))
                    .given(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> testSender.verify(validEmail))
                    .isInstanceOf(EndpointVerificationException.class)
                    .hasMessageContaining("authentication")
                    .hasMessageNotContaining("5.7.8");
        }

        @Test
        @DisplayName("mail send failure maps to EndpointVerificationException")
        void sendFailureThrowsEndpointVerificationException() {
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            willThrow(new MailSendException("SMTP server connection failed"))
                    .given(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> testSender.verify(validEmail))
                    .isInstanceOf(EndpointVerificationException.class)
                    .hasMessageContaining("unable to send email");
        }

        @Test
        @DisplayName("general mail exception maps to EndpointVerificationException")
        void generalMailExceptionThrowsEndpointVerificationException() {
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            willThrow(new MailException("Internal mail error") {})
                    .given(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> testSender.verify(validEmail))
                    .isInstanceOf(EndpointVerificationException.class)
                    .hasMessageContaining("mail service error");
        }

        @Test
        @DisplayName("invalid destination throws InvalidDestinationException and skips mail send")
        void invalidDestinationThrows() {
            willThrow(new InvalidDestinationException("Invalid email format"))
                    .given(validator).validate("bad-destination");

            assertThatThrownBy(() -> testSender.verify("bad-destination"))
                    .isInstanceOf(InvalidDestinationException.class);

            then(mailSender).shouldHaveNoInteractions();
        }
    }
}
