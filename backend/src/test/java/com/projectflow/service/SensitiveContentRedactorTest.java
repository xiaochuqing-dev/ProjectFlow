package com.projectflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SensitiveContentRedactorTest {
    private final SensitiveContentRedactor redactor = new SensitiveContentRedactor();

    @Test
    void redactsKnownFormatsAndCredentialUrlsWithoutLeakingValues() {
        String github = "gh" + "p_abcdefghijklmnopqrstuvwxyz1234567890AB";
        String aws = "AKIA" + "ABCDEFGHIJKLMNOP";
        String jwt = "eyJabcdefghijk.abcdefghijk.abcdefghijk";
        String database = "postgresql://demo:" + "super-secret-value@example.invalid/app";
        String bearer = "Bear" + "er abcdefghijklmnopqrstuvwxyz.1234567890";
        String pem = "-----BEGIN PRIVATE KEY-----\nsecret-material\n-----END PRIVATE KEY-----";
        String input = String.join("\n", github, aws, jwt, database, bearer, pem);

        String result = redactor.redact(input);

        assertThat(result).doesNotContain(
            github,
            aws,
            jwt,
            "super-secret-value",
            "abcdefghijklmnopqrstuvwxyz.1234567890",
            "secret-material"
        );
        assertThat(result).contains(SensitiveContentRedactor.REDACTED);
    }

    @Test
    void keepsOrdinaryHashesExamplesAndNaturalLanguage() {
        String sha = "0123456789abcdef0123456789abcdef01234567";
        String example = "YOUR_TOKEN_PLACEHOLDER_ABCDEFGHIJKLMNOPQRSTUVWXYZ123456";
        String text = "项目说明：Authorization 是一个 HTTP 请求头名称，不等于真实凭证。";

        assertThat(redactor.redact(String.join("\n", sha, example, text)))
            .contains(sha, example, text);
    }

    @Test
    void deniesSensitivePathsButAllowsCommittedExampleConfiguration() {
        assertThat(redactor.isSensitivePath(".env")).isTrue();
        assertThat(redactor.isSensitivePath("config/private.pem")).isTrue();
        assertThat(redactor.isSensitivePath("ops/credentials/prod.json")).isTrue();
        assertThat(redactor.isSensitivePath(".env.example")).isFalse();
        assertThat(redactor.isSensitivePath("docs/security.md")).isFalse();
    }

    @Test
    void safelyRedactsValuesNextToReplacementMetacharacters() {
        String value = "password=$uperSecretValue123456\\tail";

        assertThat(redactor.redact(value))
            .isEqualTo("password=" + SensitiveContentRedactor.REDACTED);
    }

    @Test
    void redactsWindowsUnixAndPrefixedAbsolutePathsWithoutBreakingUris() {
        String input = "cwd=C:\\Users\\demo\\private\\result.json root=/home/demo/private/result.json "
            + "evidence=file:/var/tmp/project/result.json "
            + "https=https://github.com/example/project/commit/abc "
            + "obsidian=obsidian://open?vault=Knowledge&file=ProjectFlow%2FOverview.md";

        assertThat(redactor.redactOutboundText(input))
            .doesNotContain("C:\\Users\\demo", "/home/demo", "/var/tmp")
            .contains(SensitiveContentRedactor.PATH_REDACTED)
            .contains("https://github.com/example/project/commit/abc")
            .contains("obsidian://open?vault=Knowledge&file=ProjectFlow%2FOverview.md");
    }
}
