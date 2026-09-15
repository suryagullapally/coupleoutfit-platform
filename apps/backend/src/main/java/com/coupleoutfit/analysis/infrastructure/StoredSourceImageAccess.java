package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.source.application.*;
import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class StoredSourceImageAccess implements SourceImageAccessPort {
    private final SourceAssetStorage storage;
    private final UploadPolicy policy;
    public StoredSourceImageAccess(SourceAssetStorage storage,UploadPolicy policy) { this.storage=storage;this.policy=policy; }
    @Override public AnalysisImage imageFor(SourceSnapshot source) {
        if(source.item().status()!=SourceStatus.READY_FOR_ANALYSIS) throw new AnalysisException("ANALYSIS_SOURCE_NOT_READY");
        var asset=source.asset();
        if(asset!=null) {
            if(asset.status()!=AssetStatus.VERIFIED) throw new AnalysisException("ANALYSIS_IMAGE_UNSUPPORTED");
            try {
                var head=storage.headObject(asset.storageKey());
                if(head.byteSize()>policy.maxBytes() || head.byteSize()!=asset.byteSize() || !head.mediaType().equals(asset.mediaType())) throw new IllegalArgumentException();
                try(var input=storage.openObject(asset.storageKey())) {
                    byte[] bytes=input.readNBytes(Math.toIntExact(policy.maxBytes()+1));
                    if(bytes.length!=asset.byteSize() || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(asset.sha256())) throw new IllegalArgumentException();
                    return new AnalysisImage(bytes,asset.mediaType(),null);
                }
            } catch(Exception invalid) { throw new AnalysisException("ANALYSIS_IMAGE_UNSUPPORTED"); }
        }
        var url=source.url();
        if(url==null || url.resolutionStatus()!=UrlResolutionStatus.RESOLVED_CATALOGUE_OFFER || url.resolvedProductOfferId()==null) throw new AnalysisException("ANALYSIS_IMAGE_UNSUPPORTED");
        try { return new AnalysisImage(null,null,SourceUrls.normalize(url.resolvedImageUrl())); }
        catch(IllegalArgumentException invalid) { throw new AnalysisException("ANALYSIS_IMAGE_UNSUPPORTED"); }
    }
}
