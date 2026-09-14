package com.coupleoutfit.source;

import com.coupleoutfit.source.api.AnonymousSourceSession;
import com.coupleoutfit.source.application.*;
import com.coupleoutfit.storage.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class SourceSecurityTest {
    @Test void productionCookieIsSecureSignedOpaqueAndExpires() {
        var environment=new MockEnvironment(); environment.setActiveProfiles("production");
        var now=Instant.parse("2026-01-01T00:00:00Z");
        var session=new AnonymousSourceSession(environment,Clock.fixed(now,ZoneOffset.UTC),"a-long-test-signing-key-with-32-bytes",Duration.ofDays(7),"https://coupleoutfit.com");
        var request=new MockHttpServletRequest(); var response=new MockHttpServletResponse();
        String owner=session.createOrRead(request,response); String cookie=response.getHeader("Set-Cookie");
        assertThat(cookie).contains("HttpOnly","Secure","SameSite=Strict","Path=/api/v1/source-items");
        String token=cookie.split(";",2)[0].split("=",2)[1];
        assertThat(token).doesNotContain(owner);
        request.setCookies(new Cookie(AnonymousSourceSession.COOKIE,token)); assertThat(session.require(request)).isEqualTo(owner);
        request.setCookies(new Cookie(AnonymousSourceSession.COOKIE,"x"+token.substring(1)));
        assertThatThrownBy(() -> session.require(request)).hasMessage("SOURCE_NOT_FOUND");
        request.setCookies(new Cookie(AnonymousSourceSession.COOKIE,token));
        var later=new AnonymousSourceSession(environment,Clock.fixed(now.plus(Duration.ofDays(8)),ZoneOffset.UTC),"a-long-test-signing-key-with-32-bytes",Duration.ofDays(7),"https://coupleoutfit.com");
        assertThatThrownBy(() -> later.require(request)).hasMessage("SOURCE_NOT_FOUND");
        assertThatThrownBy(() -> new AnonymousSourceSession(environment,Clock.systemUTC(),"",Duration.ofDays(7),"https://coupleoutfit.com"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void sourceOriginGuardRejectsCrossOriginBrowserWrites() {
        var environment=new MockEnvironment(); environment.setActiveProfiles("test");
        var session=new AnonymousSourceSession(environment,Clock.systemUTC(),"",Duration.ofDays(7),"https://coupleoutfit.com");
        var request=new MockHttpServletRequest(); request.addHeader("X-Source-Request","1");request.addHeader("Origin","https://attacker.example");
        assertThatThrownBy(() -> session.guardWrite(request)).hasMessage("SOURCE_REQUEST_FORBIDDEN");
        request.removeHeader("Origin");request.addHeader("Origin","https://coupleoutfit.com");session.guardWrite(request);
    }
    @Test void localStorageAndUploadControllerCannotBeEnabledInProduction() {
        var context=new ApplicationContextRunner().withUserConfiguration(StorageConfiguration.class,LocalUploadController.class)
                .withBean(Clock.class,Clock::systemUTC)
                .withBean(UploadPolicy.class,() -> new UploadPolicy(10485760,25000000,10000,Set.of("image/png","image/jpeg","image/webp"),Duration.ofMinutes(15)))
                .withPropertyValues("spring.profiles.active=production");
        context.run(c -> {assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(LocalUploadController.class);});
        context.withPropertyValues("coupleoutfit.storage.provider=local").run(c -> assertThat(c).hasFailed());
        context.withPropertyValues("spring.profiles.active=local,production","coupleoutfit.storage.provider=local").run(c -> assertThat(c).hasFailed());
    }
}
