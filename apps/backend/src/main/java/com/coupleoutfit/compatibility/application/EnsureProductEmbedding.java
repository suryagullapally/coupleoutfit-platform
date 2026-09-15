package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.catalogue.application.CatalogueRepository;
import java.util.*;
public final class EnsureProductEmbedding {
    private final CatalogueRepository catalogue;private final EnsureEmbedding ensure;private final EmbeddingDescriptorFactory descriptors;
    public EnsureProductEmbedding(CatalogueRepository catalogue,EnsureEmbedding ensure,EmbeddingDescriptorFactory descriptors){this.catalogue=catalogue;this.ensure=ensure;this.descriptors=descriptors;}
    public ProductEmbedding ensure(UUID id) {
        var p=catalogue.findProduct(id).orElseThrow(() -> new EmbeddingException("EMBEDDING_NOT_AVAILABLE"));
        var descriptor=descriptors.product(p);
        var source=new EmbeddingSource(EmbeddingSource.Kind.PRODUCT,id,p.updatedAt().toString()+"/"+descriptor.hash(),p.updatedAt());
        return new ProductEmbedding(id,ensure.ensure(source,descriptor));
    }
    public List<ProductEmbedding> prepareBatch(List<UUID> products) {
        if(products==null || products.size()>50)throw new IllegalArgumentException("Embedding batch limit is 50.");
        return products.stream().distinct().map(this::ensure).toList();
    }
}
