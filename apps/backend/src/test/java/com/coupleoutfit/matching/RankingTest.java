package com.coupleoutfit.matching;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.catalogue.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class RankingTest {
    private final CompatibilityScorer scorer=new CompatibilityScorer();
    private CompatibilityCandidate candidate(long id,String color,String pattern,double semantic) {
        var f=new CompatibilityFeatures(semantic,ProductCategory.SHIRT,ProductCategory.SHIRT,List.of("BLUE"),List.of(color),List.of("SOLID"),List.of(pattern),
                List.of("SMART"),List.of("SMART"),List.of("ELEGANT"),List.of("ELEGANT"),List.of("WINTER"),List.of("WINTER"));
        return new CompatibilityCandidate(new UUID(0,id),new UUID(0,id),new UUID(0,99),semantic,ProductCategory.SHIRT,ProductTarget.MENSWEAR,
                new ProductAttributes(Map.of()),AvailabilityStatus.IN_STOCK,f);
    }
    @ParameterizedTest @CsvSource({"100,EXCELLENT","90,EXCELLENT","89,GREAT","80,GREAT","79,GOOD","70,GOOD","69,POSSIBLE","60,POSSIBLE","59,BELOW_THRESHOLD"})
    void bands(int value,ScoreBand band){assertThat(new CoupleMatchScore(value).band()).isEqualTo(band);}
    @Test void boundsRoundingAndWeights() {
        assertThat(RankingAlgorithm.normalize(-2)).isZero();assertThat(RankingAlgorithm.normalize(2)).isEqualTo(100);
        assertThat(RankingAlgorithm.normalize(.595)).isEqualTo(60);
        assertThatThrownBy(()->RankingAlgorithm.normalize(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CoupleMatchScore(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CoupleMatchScore(101)).isInstanceOf(IllegalArgumentException.class);
        for(var mode:MatchMode.values()) {
            assertThat(RankingAlgorithm.weights(mode).stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
            assertThat(RankingAlgorithm.score(new CompatibilityScoreBreakdown(100,100,100,100,100,100,100),RankingAlgorithm.strategy(mode)).value()).isEqualTo(100);
            assertThat(RankingAlgorithm.score(new CompatibilityScoreBreakdown(0,0,0,0,0,0,0),RankingAlgorithm.strategy(mode)).value()).isZero();
        }
        assertThat(RankingAlgorithm.score(new CompatibilityScoreBreakdown(90,100,100,100,100,100,100),new ExactRankingStrategy()).value()).isEqualTo(98);
    }
    @Test void canonicalColorRulesAndUnknowns() {
        var c=new ColorRelationshipPolicy();
        assertThat(c.score(List.of("BLUE"),List.of("NAVY"),MatchMode.EXACT)).isEqualTo(90);
        assertThat(c.score(List.of("BLUE"),List.of("BLUE"),MatchMode.EXACT)).isEqualTo(100);
        assertThat(c.score(List.of("BLUE"),List.of("ORANGE"),MatchMode.COMPLEMENTARY)).isEqualTo(100);
        assertThat(c.score(List.of("BLUE"),List.of("WHITE"),MatchMode.COORDINATED)).isEqualTo(95);
        assertThat(c.score(List.of("BLUE"),List.of("PURPLE"),MatchMode.COMPLEMENTARY)).isEqualTo(45);
        assertThat(c.score(List.of("UNKNOWN"),List.of("WHITE"),MatchMode.EXACT)).isEqualTo(40);
        assertThat(c.score(List.of("MULTICOLOUR"),List.of("RED"),MatchMode.COORDINATED)).isEqualTo(40);
        assertThat(c.score(List.of("BLUE","WHITE"),List.of("WHITE","BLUE"),MatchMode.EXACT)).isEqualTo(100);
    }
    @Test void styleFormalityPatternSeasonAndCategoryRules() {
        assertThat(scorer.style(List.of("ELEGANT"),List.of("ELEGANT"))).isEqualTo(100);
        assertThat(scorer.style(List.of("ELEGANT"),List.of("TAILORED"))).isEqualTo(75);
        assertThat(scorer.style(List.of("ELEGANT"),List.of("SPORTY"))).isEqualTo(30);
        assertThat(scorer.style(List.of("UNKNOWN"),List.of("UNKNOWN"))).isEqualTo(40);
        assertThat(scorer.style(List.of("ELEGANT","CLASSIC"),List.of("ELEGANT"))).isEqualTo(50);
        assertThat(scorer.formality(List.of("SMART"),List.of("SMART_CASUAL"))).isEqualTo(75);
        assertThat(scorer.formality(List.of("FORMAL"),List.of("SPORTY"))).isZero();
        assertThat(scorer.formality(List.of("UNKNOWN"),List.of("UNKNOWN"))).isEqualTo(40);
        assertThat(scorer.pattern(List.of("SOLID"),List.of("FLORAL"),MatchMode.COORDINATED)).isEqualTo(95);
        assertThat(scorer.pattern(List.of("CHECKED"),List.of("FLORAL"),MatchMode.EXACT)).isEqualTo(25);
        assertThat(scorer.pattern(List.of(),List.of("SOLID"),MatchMode.EXACT)).isEqualTo(40);
        assertThat(scorer.seasons(List.of("WINTER"),List.of("AUTUMN","WINTER"))).isEqualTo(100);
        assertThat(scorer.seasons(List.of("WINTER"),List.of("SUMMER"))).isEqualTo(25);
        assertThat(scorer.seasons(List.of(),List.of())).isEqualTo(40);
        assertThat(scorer.category(ProductCategory.DRESS,ProductCategory.SHIRT)).isEqualTo(85);
        assertThat(scorer.category(ProductCategory.SHIRT,ProductCategory.FOOTWEAR)).isEqualTo(60);
        assertThat(scorer.category(ProductCategory.SHIRT,ProductCategory.ACCESSORY)).isZero();
        assertThat(scorer.category(ProductCategory.OTHER,ProductCategory.SHIRT)).isEqualTo(40);
    }
    @ParameterizedTest @CsvSource({"EXACT,1,2,3,95,82,72","COORDINATED,2,1,3,95,93,90","COMPLEMENTARY,3,2,1,97,93,82"})
    void goldenModes(MatchMode mode,long first,long second,long third,int score1,int score2,int score3) {
        var candidates=List.of(candidate(1,"BLUE","SOLID",.8),candidate(2,"WHITE","STRIPED",.8),candidate(3,"ORANGE","STRIPED",.7));
        var ranker=new CandidateRanker();var result=ranker.rank(candidates,mode,60,12);
        assertThat(result).extracting(r->r.candidate().productOfferId()).containsExactly(new UUID(0,first),new UUID(0,second),new UUID(0,third));
        assertThat(result).extracting(r->r.score().value()).containsExactly(score1,score2,score3);
        assertThat(result).allSatisfy(r->assertThat(r.explanation().reasons()).hasSizeBetween(2,3));
        assertThat(ranker.rank(candidates.reversed(),mode,60,12)).isEqualTo(result);
    }
    @ParameterizedTest @CsvSource({"CASUAL,RELAXED,SOLID,BLACK","SMART,ELEGANT,STRIPED,BLUE","FORMAL,TAILORED,SOLID,WHITE","SMART_CASUAL,CLASSIC,CHECKED,NAVY"})
    void curatedContextBeatsRandomMismatch(String formality,String style,String pattern,String color) {
        var aligned=new CompatibilityFeatures(.8,ProductCategory.SHIRT,ProductCategory.SHIRT,List.of(color),List.of(color),List.of(pattern),List.of(pattern),List.of(formality),List.of(formality),List.of(style),List.of(style),List.of("WINTER"),List.of("WINTER"));
        var mismatch=new CompatibilityFeatures(.1,ProductCategory.SHIRT,ProductCategory.SHIRT,List.of(color),List.of("PURPLE"),List.of(pattern),List.of("ANIMAL"),List.of(formality),List.of("SPORTY"),List.of(style),List.of("EDGY"),List.of("WINTER"),List.of("SUMMER"));
        for(var mode:MatchMode.values()) {
            var strategy=RankingAlgorithm.strategy(mode);
            assertThat(RankingAlgorithm.score(scorer.score(aligned,strategy),strategy).value()).isGreaterThan(RankingAlgorithm.score(scorer.score(mismatch,strategy),strategy).value());
        }
    }
    @Test void exactThresholdAndDeterministicTieBreaks() {
        // Unknown components=40, identical category=100 and color=100: Exact score=48+25*similarity.
        var base=candidate(1,"BLUE","SOLID",.7);
        java.util.function.BiFunction<Long,Double,CompatibilityCandidate> at=(id,sim)->{
            var f=new CompatibilityFeatures(sim,ProductCategory.SHIRT,ProductCategory.SHIRT,List.of("BLUE"),List.of("BLUE"),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
            return new CompatibilityCandidate(new UUID(0,id),new UUID(0,id),base.retailerMarketId(),sim,base.category(),base.target(),base.attributes(),AvailabilityStatus.UNKNOWN,f);
        };
        var below=at.apply(1L,.44);var boundary=at.apply(2L,.46);
        assertThat(RankingAlgorithm.score(scorer.score(below.features(),new ExactRankingStrategy()),new ExactRankingStrategy()).value()).isEqualTo(59);
        var ranker=new CandidateRanker();assertThat(ranker.rank(List.of(below,boundary),MatchMode.EXACT,60,12)).extracting(r->r.score().value()).containsExactly(60);
        var lowerId=at.apply(1L,.46);
        var stock=new CompatibilityCandidate(new UUID(0,3),new UUID(0,3),base.retailerMarketId(),.46,base.category(),base.target(),base.attributes(),AvailabilityStatus.IN_STOCK,boundary.features());
        assertThat(ranker.rank(List.of(boundary,lowerId,stock),MatchMode.EXACT,60,12)).extracting(r->r.candidate().productOfferId()).containsExactly(stock.productOfferId(),lowerId.productOfferId(),boundary.productOfferId());
    }
    @Test void explanationsNeverInventUnknownAttributes() {
        var unknown=new CompatibilityScoreBreakdown(40,40,40,40,40,40,40);
        assertThat(MatchExplanation.from(unknown,MatchMode.EXACT).reasons()).isEmpty();
        var one=new CompatibilityScoreBreakdown(40,100,40,40,40,40,40);
        assertThat(MatchExplanation.from(one,MatchMode.EXACT).reasons()).hasSize(1);
        assertThat(MatchExplanation.from(one,MatchMode.EXACT).toString()).doesNotContain("linen","material","cosine","vector");
    }
}
