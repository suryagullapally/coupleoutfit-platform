package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.EmbeddingDescriptor;
import com.coupleoutfit.catalogue.domain.Product;
import com.coupleoutfit.analysis.domain.AnalysisRevision;
import java.util.*;
public final class EmbeddingDescriptorFactory {
    public static final String VERSION="compatibility-descriptor-v1";
    private static final List<String> FIELDS=List.of("primaryColors","secondaryColors","pattern","materialAppearance","fit","formality","seasonality","styleTags","details");
    public EmbeddingDescriptor product(Product p) {
        return descriptor(p.category().name(),p.subcategory(),p.attributes().values());
    }
    public EmbeddingDescriptor analysis(AnalysisRevision r) {
        var a=r.attributes();var values=new HashMap<String,List<String>>();values.put("primaryColors",a.primaryColors());values.put("secondaryColors",a.secondaryColors());
        values.put("pattern",List.of(a.pattern().name()));values.put("materialAppearance",a.materialAppearance());values.put("fit",List.of(a.fit().name()));
        values.put("formality",List.of(a.formality().name()));values.put("seasonality",a.seasonality());values.put("styleTags",a.styleTags());values.put("details",a.details());
        return descriptor(a.category().name(),a.subcategory(),values);
    }
    private EmbeddingDescriptor descriptor(String category,String subcategory,Map<String,List<String>> values) {
        var text=new StringBuilder("descriptor: ").append(VERSION).append("\ncategory: ").append(category).append("\nsubcategory: ").append(subcategory==null?"UNKNOWN":subcategory).append('\n');
        for(String field:FIELDS) text.append(field).append(": ").append(String.join(" ",new TreeSet<>(values.getOrDefault(field,List.of())))).append('\n');
        return new EmbeddingDescriptor(VERSION,text.toString());
    }
}
