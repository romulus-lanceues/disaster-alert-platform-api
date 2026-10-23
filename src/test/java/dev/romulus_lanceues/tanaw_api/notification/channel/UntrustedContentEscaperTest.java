package dev.romulus_lanceues.tanaw_api.notification.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UntrustedContentEscaper Tests")
class UntrustedContentEscaperTest {

    @Nested
    @DisplayName("Discord Markdown Escaping")
    class DiscordMarkdownEscaping {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Returns empty string for null or empty input")
        void returnsEmptyForNullOrEmpty(String input) {
            assertThat(UntrustedContentEscaper.escapeDiscordMarkdown(input)).isEmpty();
        }

        @Test
        @DisplayName("Escapes markdown formatting delimiters")
        void escapesMarkdownDelimiters() {
            String input = "**bold** and *italic* and _underline_ and ~~strike~~ and `code` and ||spoiler||";
            String escaped = UntrustedContentEscaper.escapeDiscordMarkdown(input);

            assertThat(escaped)
                    .isEqualTo("\\*\\*bold\\*\\* and \\*italic\\* and \\_underline\\_ and \\~\\~strike\\~\\~ and \\`code\\` and \\|\\|spoiler\\|\\|");
        }

        @Test
        @DisplayName("Escapes backslashes first to prevent escape injection")
        void escapesBackslashes() {
            String input = "C:\\Windows\\System32";
            String escaped = UntrustedContentEscaper.escapeDiscordMarkdown(input);

            assertThat(escaped).isEqualTo("C:\\\\Windows\\\\System32");
        }

        @Test
        @DisplayName("Escapes markdown link syntax and headers")
        void escapesLinkAndHeaders() {
            String input = "# Header with [Malicious Link](https://attacker.example.com)";
            String escaped = UntrustedContentEscaper.escapeDiscordMarkdown(input);

            assertThat(escaped)
                    .isEqualTo("\\# Header with \\[Malicious Link\\]\\(https://attacker.example.com\\)");
        }

        @Test
        @DisplayName("Escapes quotes and angle brackets")
        void escapesQuotesAndAngleBrackets() {
            String input = "> Blockquote with <embedded> tag";
            String escaped = UntrustedContentEscaper.escapeDiscordMarkdown(input);

            assertThat(escaped).isEqualTo("\\> Blockquote with \\<embedded\\> tag");
        }

        @Test
        @DisplayName("Neutralizes @everyone and @here mentions with zero-width space")
        void neutralizesMentions() {
            String input = "Alert for @everyone and @here right now!";
            String escaped = UntrustedContentEscaper.escapeDiscordMarkdown(input);

            assertThat(escaped).doesNotContain("@everyone");
            assertThat(escaped).doesNotContain("@here");
            assertThat(escaped).contains("@\u200Beveryone");
            assertThat(escaped).contains("@\u200Bhere");
        }

        @Test
        @DisplayName("Leaves benign text unchanged")
        void leavesBenignTextUnchanged() {
            String input = "24 km SSW of Balangiga, Philippines";
            String escaped = UntrustedContentEscaper.escapeDiscordMarkdown(input);

            assertThat(escaped).isEqualTo("24 km SSW of Balangiga, Philippines");
        }

        @Test
        @DisplayName("Neutralizes Discord user and role mention tags with backslashes and zero-width spaces")
        void neutralizesDiscordMentionTags() {
            String input = "<@1234567890> and <@!1234567890> and <@&9876543210>";
            String escaped = UntrustedContentEscaper.escapeDiscordMarkdown(input);

            assertThat(escaped)
                    .doesNotContain("<@1234567890>")
                    .doesNotContain("<@!1234567890>")
                    .doesNotContain("<@&9876543210>")
                    .isEqualTo("\\<@\u200B1234567890\\> and \\<@\u200B!1234567890\\> and \\<@\u200B&9876543210\\>");
        }
    }

    @Nested
    @DisplayName("Telegram HTML Escaping")
    class TelegramHtmlEscaping {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Returns empty string for null or empty input")
        void returnsEmptyForNullOrEmpty(String input) {
            assertThat(UntrustedContentEscaper.escapeTelegramHtml(input)).isEmpty();
        }

        @Test
        @DisplayName("Escapes ampersands, angle brackets, and double quotes")
        void escapesTelegramHtmlEntities() {
            String input = "Tom & <Jerry> said \"Watch out!\"";
            String escaped = UntrustedContentEscaper.escapeTelegramHtml(input);

            assertThat(escaped).isEqualTo("Tom &amp; &lt;Jerry&gt; said &quot;Watch out!&quot;");
        }

        @Test
        @DisplayName("Prevents HTML tag injection in Telegram")
        void preventsHtmlInjection() {
            String input = "<script>alert('XSS')</script><b>Bold</b><a href=\"evil.com\">link</a>";
            String escaped = UntrustedContentEscaper.escapeTelegramHtml(input);

            assertThat(escaped).doesNotContain("<script>");
            assertThat(escaped).doesNotContain("<b>");
            assertThat(escaped).doesNotContain("<a href=");
            assertThat(escaped)
                    .isEqualTo("&lt;script&gt;alert('XSS')&lt;/script&gt;&lt;b&gt;Bold&lt;/b&gt;&lt;a href=&quot;evil.com&quot;&gt;link&lt;/a&gt;");
        }

        @Test
        @DisplayName("Leaves benign text unchanged")
        void leavesBenignTextUnchanged() {
            String input = "10 km N of San Pedro, Laguna";
            String escaped = UntrustedContentEscaper.escapeTelegramHtml(input);

            assertThat(escaped).isEqualTo("10 km N of San Pedro, Laguna");
        }
    }

    @Nested
    @DisplayName("Email HTML Escaping")
    class EmailHtmlEscaping {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Returns empty string for null or empty input")
        void returnsEmptyForNullOrEmpty(String input) {
            assertThat(UntrustedContentEscaper.escapeEmailHtml(input)).isEmpty();
        }

        @Test
        @DisplayName("Escapes ampersands, angle brackets, quotes, and apostrophes")
        void escapesStandardHtmlEntities() {
            String input = "Quake in Bob's & Alice's <Zone> \"High Risk\"";
            String escaped = UntrustedContentEscaper.escapeEmailHtml(input);

            assertThat(escaped)
                    .isEqualTo("Quake in Bob&#39;s &amp; Alice&#39;s &lt;Zone&gt; &quot;High Risk&quot;");
        }

        @Test
        @DisplayName("Leaves benign text unchanged")
        void leavesBenignTextUnchanged() {
            String input = "5 km depth in Davao City";
            String escaped = UntrustedContentEscaper.escapeEmailHtml(input);

            assertThat(escaped).isEqualTo("5 km depth in Davao City");
        }
    }
}
