package com.coupleoutfit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import com.coupleoutfit.shared.api.ApiExceptionHandler;
import com.coupleoutfit.shared.observability.RequestCorrelationFilter;
import com.coupleoutfit.shared.security.SecurityConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PlatformApiTest.ProbeController.class, properties = "coupleoutfit.cors.allowed-origins=http://localhost:3000",
        excludeAutoConfiguration = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
@Import({PlatformApiTest.ProbeController.class, ApiExceptionHandler.class,
        RequestCorrelationFilter.class, SecurityConfiguration.class})
class PlatformApiTest {
    @Autowired MockMvc mvc;

    @RestController
    static class ProbeController {
        record Payload(@NotBlank String name) {}
        @PostMapping("/__test/validate") void validate(@Valid @RequestBody Payload payload) {}
        @GetMapping("/__test/fail") void fail() { throw new IllegalStateException("password=secret SQL /private/file"); }
    }

    @Test void validationIsProblemDetailWithCorrelation() throws Exception {
        mvc.perform(post("/__test/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")
                .header("X-Request-Id", "safe-id_123"))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://coupleoutfit.com/problems/validation-failed"))
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.traceId").value("safe-id_123"))
            .andExpect(header().string("X-Request-Id", "safe-id_123"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void unexpectedErrorsAreSafeAndIdsAreReplaced() throws Exception {
        for (String inbound : new String[]{"x".repeat(65), "bad id", "bad\r\nid"}) {
            var response = mvc.perform(get("/__test/fail").header("X-Request-Id", inbound))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andReturn().getResponse();
            String id = response.getHeader("X-Request-Id");
            assertThat(id).matches("[a-f0-9-]{36}");
            assertThat(response.getContentAsString()).contains(id).doesNotContain("secret", "SQL", "/private", "IllegalStateException");
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test void malformedJsonDoesNotEchoInput() throws Exception {
        mvc.perform(post("/__test/validate").contentType(MediaType.APPLICATION_JSON).content("secret-invalid-json"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("HTTP_400"))
            .andExpect(jsonPath("$.detail").value("The request could not be processed."));
    }

    @Test void securityHeadersAndCorsAreControlled() throws Exception {
        mvc.perform(options("/__test/validate").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"));
        mvc.perform(options("/__test/validate").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get("/__test/fail").secure(true))
            .andExpect(header().exists("Strict-Transport-Security"))
            .andExpect(header().exists("Content-Security-Policy"));
    }
}
