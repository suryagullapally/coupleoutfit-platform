package com.coupleoutfit.analysis;

import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.analysis.infrastructure.ClothingSchema;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AnalysisDomainTest {
    @Test void transitionsAndCorrectionsPreserveOriginalRevision() {
        var now=Instant.now();var id=UUID.randomUUID();
        var pending=new ClothingAnalysis(id,UUID.randomUUID(),AnalysisStatus.PENDING,null,null,now,null,null,now);
        var running=pending.start(now);
        var ai=new AnalysisRevision(UUID.randomUUID(),id,1,RevisionType.AI_GENERATED,null,AnalysisFixtures.attributes(),new AttributeConfidence(.1,null,null),"1","OPENAI","test-vision","req_test","clothing-analysis-v1",now);
        var complete=running.complete(ai,now);
        var correction=new AnalysisRevision(UUID.randomUUID(),id,2,RevisionType.USER_CORRECTED,ai.id(),ai.attributes(),null,"1",null,null,null,null,now);
        assertThat(complete.correct(correction,now).currentRevisionId()).isEqualTo(correction.id());
        assertThat(complete.currentRevisionId()).isEqualTo(ai.id());
        assertThatThrownBy(() -> complete.start(now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending.complete(ai,now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> complete.correct(new AnalysisRevision(UUID.randomUUID(),id,3,RevisionType.USER_CORRECTED,UUID.randomUUID(),ai.attributes(),null,"1",null,null,null,null,now),now)).isInstanceOf(IllegalStateException.class);
        assertThat(running.fail("ANALYSIS_TIMEOUT",now).status()).isEqualTo(AnalysisStatus.FAILED);
        assertThatThrownBy(() -> new AnalysisRevision(UUID.randomUUID(),id,2,RevisionType.AI_GENERATED,null,ai.attributes(),null,"1","OPENAI","test",null,"v1",now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AnalysisRevision(UUID.randomUUID(),id,2,RevisionType.USER_CORRECTED,ai.id(),ai.attributes(),null,"1","OPENAI",null,null,null,now)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void confidenceIsBoundedAndAttributesAreImmutable() {
        for(double value:new double[]{-0.1,1.1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThatThrownBy(() -> new AttributeConfidence(value,null,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnalysisFixtures.attributes().primaryColors().add("BLUE")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void strictSchemaRejectsUnknownMissingMalformedAndUncontrolledValues() {
        var schema=new ClothingSchema();assertThat(schema.parse(AnalysisFixtures.OUTPUT).attributes().subcategory()).isEqualTo("MIDI_DRESS");
        for(String invalid:List.of("not JSON",AnalysisFixtures.OUTPUT+" {}",AnalysisFixtures.OUTPUT.replace("DRESS","ALIEN"),AnalysisFixtures.OUTPUT.replace("BURGUNDY","skin tone"),
                AnalysisFixtures.OUTPUT.replace("\"pattern\":\"SOLID\",",""),AnalysisFixtures.OUTPUT.replace("0.9","1.1"),
                AnalysisFixtures.OUTPUT.replace("\"SATIN_LIKE\"","\"100% silk\""),AnalysisFixtures.OUTPUT.replace("\"DRESS\"","4"),
                AnalysisFixtures.OUTPUT.replace("\"BURGUNDY\"","\"BURGUNDY\",\"BURGUNDY\""),AnalysisFixtures.OUTPUT.replace("\"attributes\":","\"secret\":1,\"attributes\":")))
            assertThatThrownBy(() -> schema.parse(invalid)).hasMessage("ANALYSIS_INVALID_RESPONSE");
    }
}
