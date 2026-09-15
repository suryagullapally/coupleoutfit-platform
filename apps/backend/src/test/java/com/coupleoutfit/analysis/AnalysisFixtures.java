package com.coupleoutfit.analysis;

import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.analysis.infrastructure.ClothingSchema;

public final class AnalysisFixtures {
    public static final String ATTRIBUTES="""
        {"category":"DRESS","subcategory":"MIDI_DRESS","primaryColors":["BURGUNDY"],"secondaryColors":[],
        "pattern":"SOLID","materialAppearance":["SATIN_LIKE"],"fit":"REGULAR","formality":"SMART",
        "seasonality":["AUTUMN","WINTER"],"styleTags":["ELEGANT"],"details":["MIDI","SLEEVELESS"]}
        """;
    public static final String OUTPUT="{\"attributes\":"+ATTRIBUTES+",\"confidence\":{\"category\":0.9,\"primaryColors\":0.8,\"materialAppearance\":null}}";
    public static ClothingAttributes attributes() { return new ClothingSchema().parse(OUTPUT).attributes(); }
    public static ClothingAnalysisProviderResult result() { return new ClothingAnalysisProviderResult(attributes(),new AttributeConfidence(.9,.8,null),"OPENAI","test-vision","req_test","clothing-analysis-v1"); }
}
