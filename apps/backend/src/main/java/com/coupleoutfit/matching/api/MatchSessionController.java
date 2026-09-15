package com.coupleoutfit.matching.api;
import com.coupleoutfit.matching.application.*;
import com.coupleoutfit.matching.application.MatchException;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.catalogue.domain.ProductTarget;
import com.coupleoutfit.source.api.AnonymousSourceSession;
import com.coupleoutfit.source.application.SourceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/match-sessions")
public class MatchSessionController {
    private final MatchSessions matches;private final AnonymousSourceSession owners;
    public MatchSessionController(MatchSessions matches,AnonymousSourceSession owners){this.matches=matches;this.owners=owners;}
    public record CreateRequest(UUID sourceItemId,UUID analysisRevisionId,ProductTarget partnerTarget,MatchMode matchMode,boolean sameRetailerOnly) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void unknown(String name,Object value){throw new IllegalArgumentException("Unknown match request field.");}
        CreateMatchCommand command(){return new CreateMatchCommand(sourceItemId,analysisRevisionId,partnerTarget,matchMode,sameRetailerOnly);}
    }
    public record Configuration(ProductTarget partnerTarget,MatchMode matchMode,boolean sameRetailerOnly) {}
    public record Result(UUID resultId,int rank,int coupleMatchScore,ScoreBand scoreBand,MatchExplanation explanation,
            MatchResultView.Product product,MatchResultView.Retailer retailer,MatchResultView.Offer offer) {}
    public record Response(UUID id,UUID sourceItemId,UUID analysisRevisionId,MatchStatus status,MarketContext market,Configuration configuration,
            String algorithmVersion,String failureCode,List<Result> results) {}
    private String owner(HttpServletRequest request) {
        try{return owners.require(request);}catch(SourceException e){throw new MatchException("MATCH_SESSION_NOT_FOUND");}
    }
    private ResponseEntity<Response> response(MatchSession session) {
        var results=matches.results(session).stream().map(v->{var r=v.result();return new Result(r.id(),r.rank(),r.coupleMatchScore().value(),r.scoreBand(),r.explanation(),v.product(),v.retailer(),v.offer());}).toList();
        return ResponseEntity.status(session.status()==MatchStatus.MATCHING?HttpStatus.ACCEPTED:HttpStatus.OK).cacheControl(CacheControl.noStore()).body(
                new Response(session.id(),session.sourceItemId(),session.analysisRevisionId(),session.status(),session.market(),
                    new Configuration(session.partnerTarget(),session.matchMode(),session.sameRetailerOnly()),session.algorithmVersion(),session.failureCode(),results));
    }
    @PostMapping public ResponseEntity<Response> create(@RequestBody CreateRequest body,@RequestHeader("Idempotency-Key") UUID key,HttpServletRequest request) {
        owners.guardWrite(request);return response(matches.create(body.command(),owner(request),key));
    }
    @GetMapping("/{id}") public ResponseEntity<Response> get(@PathVariable UUID id,HttpServletRequest request){return response(matches.get(id,owner(request)));}
}
