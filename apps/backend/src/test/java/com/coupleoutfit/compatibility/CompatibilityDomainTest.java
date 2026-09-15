package com.coupleoutfit.compatibility;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.analysis.AnalysisFixtures;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
class CompatibilityDomainTest {
    @Test void descriptorIsDeterministicAndCommercialDataCannotEnterIt() {
        var now=Instant.now();var id=UUID.randomUUID();var retailer=UUID.randomUUID();
        var first=new Product(id,retailer,"SECRET PRICE 99 GBP https://commercial.example","BRAND",ProductCategory.SHIRT,null,ProductTarget.MENSWEAR,
                new ProductAttributes(Map.of("primaryColors",List.of("BLUE","NAVY"),"styleTags",List.of("SMART","CLASSIC"))),"1",ProductStatus.ACTIVE,now,now);
        var second=new Product(id,retailer,"A different name",null,ProductCategory.SHIRT,null,ProductTarget.WOMENSWEAR,
                new ProductAttributes(Map.of("styleTags",List.of("CLASSIC","SMART"),"primaryColors",List.of("NAVY","BLUE"))),"1",ProductStatus.ACTIVE,now,now);
        var factory=new EmbeddingDescriptorFactory();var a=factory.product(first);var b=factory.product(second);
        assertThat(a).isEqualTo(b);assertThat(a.hash()).isEqualTo(b.hash());assertThat(a.version()).isEqualTo("compatibility-descriptor-v1");
        assertThat(a.text()).doesNotContain("SECRET","PRICE","GBP","http","BRAND","price","currency","affiliateUrl","productUrl","retailerProductId","availability","commission","feed","MENSWEAR","WOMENSWEAR");
        assertThat(a.text()).contains("primaryColors: BLUE NAVY","styleTags: CLASSIC SMART");
    }
    @Test void analysisTokenOrderAndProviderMetadataDoNotChangeDescriptor() {
        var a=AnalysisFixtures.attributes();var now=Instant.now();
        var b=new com.coupleoutfit.analysis.domain.ClothingAttributes(a.category(),a.subcategory(),a.primaryColors(),a.secondaryColors(),a.pattern(),a.materialAppearance(),a.fit(),a.formality(),List.of("WINTER","AUTUMN"),a.styleTags(),List.of("SLEEVELESS","MIDI"));
        var first=new com.coupleoutfit.analysis.domain.AnalysisRevision(UUID.randomUUID(),UUID.randomUUID(),1,com.coupleoutfit.analysis.domain.RevisionType.AI_GENERATED,null,a,null,"1","OPENAI","model-a",null,"prompt-a",now);
        var second=new com.coupleoutfit.analysis.domain.AnalysisRevision(UUID.randomUUID(),UUID.randomUUID(),1,com.coupleoutfit.analysis.domain.RevisionType.AI_GENERATED,null,b,null,"1","OPENAI","model-b",null,"prompt-b",now);
        var factory=new EmbeddingDescriptorFactory();assertThat(factory.analysis(first)).isEqualTo(factory.analysis(second));
        assertThat(factory.analysis(first).text()).doesNotContain("model-a","prompt-a",first.id().toString());
    }
    @Test void vectorsRejectNonFiniteZeroAndInvalidDimensionsWithoutPadding() {
        for(List<Float> values:List.of(List.<Float>of(),List.of(0f,0f),List.of(Float.NaN),List.of(Float.POSITIVE_INFINITY)))
            assertThatThrownBy(() -> new EmbeddingVector(values)).isInstanceOf(IllegalArgumentException.class);
        var vector=new EmbeddingVector(List.of(1f,0f));assertThatThrownBy(() -> vector.values().add(1f)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new EmbeddingProvider.Result(vector,new EmbeddingModelMetadata("OPENAI","test",3),null)).hasMessage("EMBEDDING_DIMENSION_MISMATCH");
        assertThat(vector.toString()).isEqualTo("EmbeddingVector[dimensions=2]");
    }
    @Test void requestsAndCategoryPolicyAreExplicitAndFeaturesAreNotScores() {
        assertThatThrownBy(() -> new CompatibilityRequest(UUID.randomUUID(),new MarketContext("GB","GBP","en-GB"),ProductTarget.MENSWEAR,ProductCategory.DRESS,201)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new CategoryEligibilityPolicy().allowedFor(ProductCategory.DRESS)).contains(ProductCategory.SHIRT,ProductCategory.SUIT).doesNotContain(ProductCategory.ACCESSORY);
        var features=CompatibilityFeatures.from(.5,AnalysisFixtures.attributes(),ProductCategory.SHIRT,new ProductAttributes(Map.of("primaryColors",List.of("BLUE"))));
        assertThat(features.sourceColors()).contains("BURGUNDY");assertThat(features.candidateColors()).containsExactly("BLUE");
        assertThat(features.candidatePattern()).isEmpty();assertThat(features.semanticSimilarity()).isEqualTo(.5);
    }
}
