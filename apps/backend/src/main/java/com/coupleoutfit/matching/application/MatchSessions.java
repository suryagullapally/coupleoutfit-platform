package com.coupleoutfit.matching.application;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.compatibility.application.RetrieveCompatibilityCandidates;
import com.coupleoutfit.compatibility.domain.CompatibilityRequest;
import java.time.Clock;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
public final class MatchSessions {
    private final MatchSessionRepository repository;
    private final RetrieveCompatibilityCandidates retrieval;
    private final Clock clock;
    private final int candidateLimit,resultLimit,minimumScore;
    public MatchSessions(MatchSessionRepository repository,RetrieveCompatibilityCandidates retrieval,Clock clock,int candidateLimit,int resultLimit,int minimumScore) {
        this.repository=repository;this.retrieval=retrieval;this.clock=clock;
        if(candidateLimit<1 || candidateLimit>200 || resultLimit<1 || resultLimit>50 || resultLimit>candidateLimit || minimumScore<60 || minimumScore>100)
            throw new IllegalArgumentException("Invalid matching limits.");
        this.candidateLimit=candidateLimit;this.resultLimit=resultLimit;this.minimumScore=minimumScore;
    }
    public MatchSession create(CreateMatchCommand command,String owner,UUID idempotencyKey) {
        if(idempotencyKey==null)throw new MatchException("MATCH_REQUEST_INVALID");
        String key;
        try {key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((owner+"|"+idempotencyKey).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        var session=repository.createOrFind(command,owner,key,candidateLimit,resultLimit,minimumScore,clock.instant());
        if(repository.claim(session.id(),clock.instant())) {
            String failure="MATCH_RETRIEVAL_FAILED";
            try {
                var request=new CompatibilityRequest(session.analysisRevisionId(),session.market(),session.partnerTarget(),session.sourceAttributes().category(),
                        session.candidateLimit(),session.sameRetailerOnly()?session.sourceRetailerMarketId():null);
                var candidates=retrieval.retrieveSnapshot(request,repository.revision(session.id()));
                failure="MATCH_SCORING_FAILED";
                var ranked=new CandidateRanker().rank(candidates,session.matchMode(),session.minimumScore(),session.resultLimit());
                var now=clock.instant();var results=new ArrayList<MatchResult>();
                for(var candidate:ranked)results.add(new MatchResult(UUID.randomUUID(),session.id(),candidate.candidate().productOfferId(),results.size()+1,
                        candidate.score(),candidate.score().band(),candidate.breakdown(),candidate.explanation(),session.algorithmVersion(),now));
                failure="MATCH_PERSISTENCE_FAILED";
                repository.complete(session.id(),results,now);
            } catch(RuntimeException e) {repository.fail(session.id(),failure,clock.instant());}
        }
        return repository.findOwned(session.id(),owner);
    }
    public MatchSession get(UUID id,String owner){return repository.findOwned(id,owner);}
    public List<MatchResultView> results(MatchSession session){return session.status()==MatchStatus.COMPLETED?repository.results(session.id()):List.of();}
}
