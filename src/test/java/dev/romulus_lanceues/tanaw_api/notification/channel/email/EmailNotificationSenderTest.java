package dev.romulus_lanceues.tanaw_api.notification.channel.email;

import dev.romulus_lanceues.tanaw_api.notification.NotificationProperties;
import dev.romulus_lanceues.tanaw_api.notification.channel.AlertMessage;
import dev.romulus_lanceues.tanaw_api.notification.channel.DeliveryResult;
import dev.romulus_lanceues.tanaw_api.notification.channel.InvalidDestinationException;
import dev.romulus_lanceues.tanaw_api.notification.channel.NotificationChannel;
import dev.romulus_lanceues.tanaw_api.notification.destination.DestinationMasker;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationSender Unit Tests")
class EmailNotificationSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailDestinationValidator validator;

    private DestinationMasker masker;
    private EmailNotificationSender sender;

    private NotificationProperties propertiesDryRun;
    private NotificationProperties propertiesLive;

    private final String validEmail = "user@example.com";
    private final Instant testOccurredAt = Instant.parse("2026-11-07T03:00:00Z");

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

    private AlertMessage createSampleMessage(String severity) {
        return new AlertMessage(
                "M 5.2 - 12 km ENE of Davao City",
                "12 km ENE of Davao City",
                5.2,
                "M 5.2",
                15.4,
                "15.4 km from Home",
                "Home",
                10.0,
                "10.0 km",
                severity,
                testOccurredAt,
                "2026-11-07 11:00:00 PHT",
                "2026-11-07 03:00:00 UTC",
                "REVIEWED",
                "Reviewed by US Geological Survey",
                "https://earthquake.usgs.gov/earthquakes/eventpage/us123456",
                "Tanaw Early Earthquake Warning System"
        );
    }

    @Test
    @DisplayName("channel returns NotificationChannel.EMAIL")
    void channelReturnsEmail() {
        sender = new EmailNotificationSender(mailSender, propertiesDryRun, validator, masker);
        assertThat(sender.channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @BeforeEach
        void initSender() {
            sender = new EmailNotificationSender(mailSender, propertiesLive, validator, masker);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("destination null or blank returns Permanent('DESTINATION_BLANK', true)")
        void nullOrBlankDestinationReturnsPermanent(String destination) {
            DeliveryResult result = sender.send(createSampleMessage("MODERATE"), destination);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.reason()).isEqualTo("DESTINATION_BLANK");
            assertThat(permanent.invalidateEndpoint()).isTrue();
            then(validator).shouldHaveNoInteractions();
            then(mailSender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("invalid destination syntax returns Permanent('INVALID_DESTINATION', true)")
        void invalidDestinationSyntaxReturnsPermanent() {
            willThrow(new InvalidDestinationException("Invalid email format"))
                    .given(validator).validate("bad-email");

            DeliveryResult result = sender.send(createSampleMessage("MODERATE"), "bad-email");

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.reason()).isEqualTo("INVALID_DESTINATION");
            assertThat(permanent.invalidateEndpoint()).isTrue();
            then(mailSender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("null message returns Permanent('ALERT_MESSAGE_NULL', false)")
        void nullMessageReturnsPermanent() {
            willDoNothing().given(validator).validate(validEmail);

            DeliveryResult result = sender.send(null, validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.reason()).isEqualTo("ALERT_MESSAGE_NULL");
            assertThat(permanent.invalidateEndpoint()).isFalse();
            then(mailSender).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Dry-Run Mode")
    class DryRunModeTests {

        @BeforeEach
        void initSender() {
            sender = new EmailNotificationSender(mailSender, propertiesDryRun, validator, masker);
        }

        @Test
        @DisplayName("validates destination and returns Sent('dry-run') without sending outbound email")
        void dryRunReturnsSentWithoutNetworkConnection() {
            willDoNothing().given(validator).validate(validEmail);

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
            DeliveryResult.Sent sent = (DeliveryResult.Sent) result;
            assertThat(sent.providerMessageId()).isEqualTo("dry-run");

            then(validator).should().validate(validEmail);
            then(mailSender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("dry-run validates destination before skipping send")
        void dryRunValidatesDestination() {
            willThrow(new InvalidDestinationException("Invalid email format"))
                    .given(validator).validate("invalid-email");

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), "invalid-email");

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.reason()).isEqualTo("INVALID_DESTINATION");
            assertThat(permanent.invalidateEndpoint()).isTrue();

            then(mailSender).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("Live Send Mode")
    class LiveSendModeTests {

        @BeforeEach
        void initSender() {
            sender = new EmailNotificationSender(mailSender, propertiesLive, validator, masker);
        }

        @Test
        @DisplayName("successfully constructs and delivers multipart email with RFC 5322 Message-ID header")
        void successfullySendsMultipartEmail() throws MessagingException, IOException {
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            willDoNothing().given(mailSender).send(any(MimeMessage.class));

            AlertMessage message = createSampleMessage("CRITICAL");
            DeliveryResult result = sender.send(message, validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
            DeliveryResult.Sent sent = (DeliveryResult.Sent) result;

            // Deterministic Message-ID
            String expectedSeed = validEmail + "|" + message.title() + "|" + message.occurredAt();
            UUID expectedUuid = UUID.nameUUIDFromBytes(expectedSeed.getBytes(StandardCharsets.UTF_8));
            String expectedMessageId = "<" + expectedUuid + "@tanaw.dev>";
            assertThat(sent.providerMessageId()).isEqualTo(expectedMessageId);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            then(mailSender).should().send(captor.capture());

            MimeMessage sentMessage = captor.getValue();
            assertThat(sentMessage.getHeader("Message-ID", null)).isEqualTo(expectedMessageId);
            assertThat(sentMessage.getSubject()).isEqualTo("M 5.2 - 12 km ENE of Davao City");

            Address[] from = sentMessage.getFrom();
            assertThat(from).hasSize(1);
            InternetAddress fromAddress = (InternetAddress) from[0];
            assertThat(fromAddress.getAddress()).isEqualTo("alerts@tanaw.dev");
            assertThat(fromAddress.getPersonal()).isEqualTo("Tanaw Alerts");

            Address[] to = sentMessage.getRecipients(Message.RecipientType.TO);
            assertThat(to).hasSize(1);
            assertThat(((InternetAddress) to[0]).getAddress()).isEqualTo(validEmail);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            sentMessage.writeTo(baos);
            String raw = baos.toString(StandardCharsets.UTF_8);

            assertThat(raw).contains("text/plain");
            assertThat(raw).contains("text/html");
            assertThat(raw).contains("M 5.2 - 12 km ENE of Davao City");
            assertThat(raw).contains("12 km ENE of Davao City");
            assertThat(raw).contains("15.4 km from Home");
            assertThat(raw).contains("Home");
            assertThat(raw).contains("10.0 km");
            assertThat(raw).contains("2026-11-07 11:00:00 PHT");
            assertThat(raw).contains("2026-11-07 03:00:00 UTC");
            assertThat(raw).contains("CRITICAL");
            assertThat(raw).contains("#E74C3C"); // Critical color badge
            assertThat(raw).contains("https://earthquake.usgs.gov/earthquakes/eventpage/us123456");
            assertThat(raw).contains("Tanaw Early Earthquake Warning System");
        }

        @Test
        @DisplayName("properly escapes untrusted HTML content to prevent XSS and HTML injection")
        void properlyEscapesUntrustedHtml() throws MessagingException, IOException {
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            willDoNothing().given(mailSender).send(any(MimeMessage.class));

            AlertMessage untrustedMessage = new AlertMessage(
                    "<script>alert('xss')</script> Earthquake",
                    "<b>Dangerous Place</b> & 'quoted'",
                    4.5,
                    "M 4.5 <img src=x onerror=alert(1)>",
                    10.0,
                    "10 km <style>",
                    "Work <script>",
                    5.0,
                    "5 km",
                    "MODERATE",
                    testOccurredAt,
                    "2026-11-07 11:00:00 PHT",
                    "2026-11-07 03:00:00 UTC",
                    "AUTOMATIC",
                    "Notice & warning <script>",
                    "https://earthquake.usgs.gov/event?id=123&test=1",
                    "Footer & 'test' <marquee>"
            );

            DeliveryResult result = sender.send(untrustedMessage, validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Sent.class);

            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            then(mailSender).should().send(captor.capture());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            captor.getValue().writeTo(baos);
            String raw = baos.toString(StandardCharsets.UTF_8);

            // Verify raw dangerous HTML tags are escaped in the HTML part
            assertThat(raw).contains("text/html");
            String htmlPart = raw.substring(raw.indexOf("text/html"));

            assertThat(htmlPart).doesNotContain("<script>alert('xss')</script>");
            assertThat(htmlPart).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
            assertThat(htmlPart).doesNotContain("<b>Dangerous Place</b>");
            assertThat(htmlPart).contains("&lt;b&gt;Dangerous Place&lt;/b&gt; &amp; &#39;quoted&#39;");
            assertThat(htmlPart).doesNotContain("<img src=x onerror=alert(1)>");
            assertThat(htmlPart).contains("&lt;img src=x onerror=alert(1)&gt;");
            assertThat(htmlPart).doesNotContain("<style>");
            assertThat(htmlPart).contains("&lt;style&gt;");
            assertThat(htmlPart).doesNotContain("<marquee>");
            assertThat(htmlPart).contains("&lt;marquee&gt;");
        }

        @Test
        @DisplayName("severity badge colors map correctly for all severities")
        void severityBadgeColorsMapCorrectly() throws MessagingException, IOException {
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());

            // HIGH -> #E67E22
            MimeMessage msgHigh = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(msgHigh);
            sender.send(createSampleMessage("HIGH"), validEmail);
            ByteArrayOutputStream baosHigh = new ByteArrayOutputStream();
            msgHigh.writeTo(baosHigh);
            assertThat(baosHigh.toString(StandardCharsets.UTF_8)).contains("#E67E22");

            // MODERATE -> #F1C40F
            MimeMessage msgMod = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(msgMod);
            sender.send(createSampleMessage("MODERATE"), validEmail);
            ByteArrayOutputStream baosMod = new ByteArrayOutputStream();
            msgMod.writeTo(baosMod);
            assertThat(baosMod.toString(StandardCharsets.UTF_8)).contains("#F1C40F");

            // LOW / Default -> #3498DB
            MimeMessage msgLow = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(msgLow);
            sender.send(createSampleMessage("LOW"), validEmail);
            ByteArrayOutputStream baosLow = new ByteArrayOutputStream();
            msgLow.writeTo(baosLow);
            assertThat(baosLow.toString(StandardCharsets.UTF_8)).contains("#3498DB");
        }

        @Test
        @DisplayName("falls back to default fromAddress, fromName, and subject when not configured")
        void fallsBackToDefaults() throws MessagingException {
            NotificationProperties minimalProperties = new NotificationProperties(
                    false,
                    new NotificationProperties.Dispatcher(true, Duration.ofSeconds(5), 50, Duration.ofMinutes(2)),
                    new NotificationProperties.Retry(5, Duration.ofSeconds(10), Duration.ofMinutes(10)),
                    new NotificationProperties.Fanout(Duration.ofHours(6)),
                    new NotificationProperties.Discord(Duration.ofSeconds(3), Duration.ofSeconds(10)),
                    new NotificationProperties.Crypto("dGhpcy1pcy1hLXZlcnktc2VjdXJlLTI1Ni1iaXQta2V5LTEyMzQ1Ng=="),
                    null,
                    null
            );
            EmailNotificationSender defaultSender = new EmailNotificationSender(mailSender, minimalProperties, validator, masker);
            willDoNothing().given(validator).validate(validEmail);

            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            AlertMessage messageWithoutTitle = new AlertMessage(
                    null, null, 3.0, null, null, null, null, null, null, null, null, null, null, null, null, null, null
            );

            DeliveryResult result = defaultSender.send(messageWithoutTitle, validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Sent.class);
            ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
            then(mailSender).should().send(captor.capture());

            MimeMessage sentMessage = captor.getValue();
            assertThat(sentMessage.getSubject()).isEqualTo("Earthquake alert");
            InternetAddress from = (InternetAddress) sentMessage.getFrom()[0];
            assertThat(from.getAddress()).isEqualTo("no-reply@tanaw.dev");
            assertThat(from.getPersonal()).isEqualTo("Tanaw Alerts");
        }
    }

    @Nested
    @DisplayName("Error Classification into DeliveryResult")
    class ErrorClassificationTests {

        @BeforeEach
        void initSender() {
            sender = new EmailNotificationSender(mailSender, propertiesLive, validator, masker);
            willDoNothing().given(validator).validate(validEmail);
        }

        @Test
        @DisplayName("MailAuthenticationException returns Retryable('EMAIL_AUTH_ERROR')")
        void mailAuthExceptionReturnsRetryable() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);
            willThrow(new MailAuthenticationException("535 5.7.8 Authentication credentials invalid"))
                    .given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("EMAIL_AUTH_ERROR");
        }

        @Test
        @DisplayName("nested jakarta.mail.AuthenticationFailedException in MailSendException returns Retryable('EMAIL_AUTH_ERROR')")
        void nestedAuthFailedExceptionReturnsRetryable() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            MailSendException ex = new MailSendException("Send failed", new jakarta.mail.AuthenticationFailedException("Auth error"));
            willThrow(ex).given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("EMAIL_AUTH_ERROR");
        }

        @Test
        @DisplayName("invalid recipient with SendFailedException returns Permanent('INVALID_RECIPIENT', true)")
        void invalidRecipientReturnsPermanent() throws Exception {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            SendFailedException sfe = new SendFailedException("Invalid address", null, null, null,
                    new Address[]{new InternetAddress("unknown@domain.com")});
            MailSendException ex = new MailSendException("Delivery failed", sfe);
            willThrow(ex).given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.reason()).isEqualTo("INVALID_RECIPIENT");
            assertThat(permanent.invalidateEndpoint()).isTrue();
        }

        @Test
        @DisplayName("SMTP 550 User unknown returns Permanent('INVALID_RECIPIENT', true)")
        void smtp550ReturnsPermanent() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            MailSendException ex = new MailSendException("550 5.1.1 User unknown");
            willThrow(ex).given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.reason()).isEqualTo("INVALID_RECIPIENT");
            assertThat(permanent.invalidateEndpoint()).isTrue();
        }

        @Test
        @DisplayName("SMTP 553 Invalid recipient address returns Permanent('INVALID_RECIPIENT', true)")
        void smtp553ReturnsPermanent() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            MailSendException ex = new MailSendException("553 Invalid recipient address");
            willThrow(ex).given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Permanent.class);
            DeliveryResult.Permanent permanent = (DeliveryResult.Permanent) result;
            assertThat(permanent.reason()).isEqualTo("INVALID_RECIPIENT");
            assertThat(permanent.invalidateEndpoint()).isTrue();
        }

        @Test
        @DisplayName("SocketTimeoutException returns Retryable('TIMEOUT')")
        void socketTimeoutReturnsRetryableTimeout() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            MailSendException ex = new MailSendException("Connection timed out", new SocketTimeoutException("Read timed out"));
            willThrow(ex).given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("TIMEOUT");
        }

        @Test
        @DisplayName("transient MailSendException returns Retryable('EMAIL_SEND_FAILED')")
        void transientMailSendExceptionReturnsRetryable() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            MailSendException ex = new MailSendException("Temporary network glitch");
            willThrow(ex).given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("EMAIL_SEND_FAILED");
        }

        @Test
        @DisplayName("general MailException returns Retryable('MAIL_SERVICE_ERROR')")
        void generalMailExceptionReturnsRetryable() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            willThrow(new MailException("Internal mail error") {})
                    .given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("MAIL_SERVICE_ERROR");
        }

        @Test
        @DisplayName("unexpected runtime exception during send returns Retryable('UNEXPECTED_ERROR: ...')")
        void unexpectedRuntimeExceptionReturnsRetryable() {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            given(mailSender.createMimeMessage()).willReturn(mimeMessage);

            willThrow(new IllegalStateException("Unexpected state"))
                    .given(mailSender).send(any(MimeMessage.class));

            DeliveryResult result = sender.send(createSampleMessage("HIGH"), validEmail);

            assertThat(result).isInstanceOf(DeliveryResult.Retryable.class);
            DeliveryResult.Retryable retryable = (DeliveryResult.Retryable) result;
            assertThat(retryable.reason()).isEqualTo("UNEXPECTED_ERROR: IllegalStateException");
        }
    }
}
