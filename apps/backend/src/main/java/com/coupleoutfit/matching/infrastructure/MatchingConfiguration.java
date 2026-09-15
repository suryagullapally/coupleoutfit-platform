package com.coupleoutfit.matching.infrastructure;
import com.coupleoutfit.matching.application.*;
import com.coupleoutfit.compatibility.application.RetrieveCompatibilityCandidates;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
@Configuration(proxyBeanMethods=false)
public class MatchingConfiguration {
    @Bean MatchSessions matchSessions(MatchSessionRepository repository,RetrieveCompatibilityCandidates retrieval,Clock clock,
            @Value("${coupleoutfit.matching.candidate-limit:50}") int candidates,
            @Value("${coupleoutfit.matching.result-limit:12}") int results,
            @Value("${coupleoutfit.matching.minimum-score:60}") int minimum) {
        return new MatchSessions(repository,retrieval,clock,candidates,results,minimum);
    }
}
