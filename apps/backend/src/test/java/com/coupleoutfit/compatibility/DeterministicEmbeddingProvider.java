package com.coupleoutfit.compatibility;
import com.coupleoutfit.compatibility.application.EmbeddingProvider;
import com.coupleoutfit.compatibility.domain.*;
import java.util.*;
/** Fixture geometry only, never a production provider or fashion-quality assertion. */
public final class DeterministicEmbeddingProvider implements EmbeddingProvider {
    private final EmbeddingModelMetadata model;
    public DeterministicEmbeddingProvider(EmbeddingModelMetadata model){this.model=model;}
    @Override public Result embed(EmbeddingDescriptor descriptor) {
        var values=new ArrayList<>(Collections.nCopies(model.dimensions(),0f));
        if(descriptor.text().contains("primaryColors: BLUE")){values.set(0,1f);}
        else if(descriptor.text().contains("primaryColors: NAVY")){values.set(0,.8f);values.set(1,.6f);}
        else {values.set(1,1f);}
        return new Result(new EmbeddingVector(values),model,"req_fixture");
    }
}
