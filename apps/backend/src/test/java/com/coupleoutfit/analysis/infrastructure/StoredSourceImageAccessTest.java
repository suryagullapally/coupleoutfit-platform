package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.source.application.*;
import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoredSourceImageAccessTest {
    private final SourceAssetStorage storage=mock(SourceAssetStorage.class);
    private final StoredSourceImageAccess access=new StoredSourceImageAccess(storage,new UploadPolicy(10485760,25000000,10000,Set.of("image/png"),Duration.ofMinutes(15)));
    private SourceItem item(SourceType type,SourceStatus status) {
        var now=Instant.now();return new SourceItem(UUID.randomUUID(),"a".repeat(64),type,status,new MarketContext("GB","GBP","en-GB"),now,now);
    }
    @Test void privateVerifiedBytesAreBoundedClonedAndIntegrityChecked() throws Exception {
        var item=item(SourceType.IMAGE_UPLOAD,SourceStatus.READY_FOR_ANALYSIS);byte[] bytes={1,2,3};
        var asset=SourceAsset.pending(item.id(),item.createdAt()).verified("image/png",bytes.length,2,3,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),Instant.now());
        when(storage.headObject(asset.storageKey())).thenReturn(new SourceAssetStorage.ObjectMetadata(3,"image/png"));
        when(storage.openObject(asset.storageKey())).thenReturn(new ByteArrayInputStream(bytes));
        var image=access.imageFor(new SourceSnapshot(item,asset,null));assertThat(image.bytes()).containsExactly(bytes);
        var copy=image.bytes();copy[0]=9;assertThat(image.bytes()[0]).isEqualTo((byte)1);
        assertThat(image.toString()).doesNotContain("AQID","source-items");
        when(storage.openObject(asset.storageKey())).thenReturn(new ByteArrayInputStream(new byte[]{9,2,3}));
        assertThatThrownBy(() -> access.imageFor(new SourceSnapshot(item,asset,null))).hasMessage("ANALYSIS_IMAGE_UNSUPPORTED");
        verify(storage,never()).createTemporaryReadUrl(any());
    }
    @Test void onlyReadyResolvedCatalogueUrlsBecomeProviderInput() {
        var item=item(SourceType.PRODUCT_URL,SourceStatus.READY_FOR_ANALYSIS);
        var url=new SourceUrl(item.id(),"https://shop.example.com/p","https://shop.example.com/p",UrlResolutionStatus.RESOLVED_CATALOGUE_OFFER,
                "CATALOGUE",UUID.randomUUID(),UUID.randomUUID(),"https://cdn.example.com/photo.png",null,Instant.now());
        assertThat(access.imageFor(new SourceSnapshot(item,null,url)).trustedImageUrl()).isEqualTo("https://cdn.example.com/photo.png");
        assertThatThrownBy(() -> access.imageFor(new SourceSnapshot(item,null,null))).hasMessage("ANALYSIS_IMAGE_UNSUPPORTED");
        var waiting=item(SourceType.PRODUCT_URL,SourceStatus.IMAGE_UPLOAD_REQUIRED);
        assertThatThrownBy(() -> access.imageFor(new SourceSnapshot(waiting,null,null))).hasMessage("ANALYSIS_SOURCE_NOT_READY");
        verifyNoInteractions(storage);
    }
}
