package com.coupleoutfit.source;

import com.coupleoutfit.source.application.*;
import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.source.infrastructure.StoredImageVerifier;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import com.coupleoutfit.storage.infrastructure.LocalSourceAssetStorageAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import static org.assertj.core.api.Assertions.*;

class SourceStorageTest {
    @TempDir Path root;
    private static final Instant NOW=Instant.parse("2026-01-01T00:00:00Z");
    private static UploadPolicy policy(long bytes,long pixels) {
        return new UploadPolicy(bytes,pixels,10000,Set.of("image/png","image/jpeg","image/webp"),Duration.ofMinutes(15));
    }
    static byte[] fixture(String type) throws IOException {
        try(var in=SourceStorageTest.class.getResourceAsStream("/images/tiny."+type)) { return Objects.requireNonNull(in).readAllBytes(); }
    }
    private String token(String url) { return url.substring(url.indexOf("token=")+6); }
    private void upload(LocalSourceAssetStorageAdapter store,SourceAsset asset,String type,byte[] data) {
        store.upload(token(store.createUploadAuthorization(asset.storageKey()).uploadUrl()),type,new ByteArrayInputStream(data));
    }
    @Test void supportedImagesDecodeWithDimensionsAndAuthoritativeHash() throws Exception {
        var policy=policy(10485760,25000000); var store=new LocalSourceAssetStorageAdapter(root,policy,Clock.fixed(NOW,ZoneOffset.UTC));
        for(String type:List.of("png","jpeg","webp")) {
            var asset=SourceAsset.pending(UUID.randomUUID(),NOW); byte[] data=fixture(type);
            upload(store,asset,"image/"+type,data);
            var result=new StoredImageVerifier(store,policy,1).verify(asset,NOW);
            assertThat(result.status()).isEqualTo(AssetStatus.VERIFIED);
            assertThat(result.width()).isEqualTo(2); assertThat(result.height()).isEqualTo(3);
            assertThat(result.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
            assertThat(result.byteSize()).isEqualTo((long)data.length);
        }
    }
    @Test void capabilitiesAreWriteOnceScopedAndSupportPrivateReadDelete() throws Exception {
        var store=new LocalSourceAssetStorageAdapter(root,policy(10485760,25000000),Clock.fixed(NOW,ZoneOffset.UTC));
        var asset=SourceAsset.pending(UUID.randomUUID(),NOW); var authorization=store.createUploadAuthorization(asset.storageKey());
        assertThatThrownBy(() -> store.upload("forged", "image/png",new ByteArrayInputStream(fixture("png")))).isInstanceOf(SourceException.class);
        store.upload(token(authorization.uploadUrl()),"image/png",new ByteArrayInputStream(fixture("png")));
        assertThatThrownBy(() -> store.upload(token(authorization.uploadUrl()),"image/png",new ByteArrayInputStream(fixture("png")))).isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> upload(store,asset,"image/png",fixture("png"))).isInstanceOf(SourceException.class);
        assertThat(store.headObject(asset.storageKey()).byteSize()).isEqualTo(fixture("png").length);
        String read=token(store.createTemporaryReadUrl(asset.storageKey()));
        try(var input=store.readAuthorized(read)) { assertThat(input.readAllBytes()).isEqualTo(fixture("png")); }
        store.deleteObject(asset.storageKey());
        assertThatThrownBy(() -> store.readAuthorized(read)).isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> store.headObject(asset.storageKey())).isInstanceOf(SourceException.class);
        assertThatThrownBy(() -> store.createUploadAuthorization("../../escape")).isInstanceOf(SourceException.class);
    }
    @Test void expiredCapabilitiesAndSymlinkEscapeAreRejected() throws Exception {
        class MutableClock extends Clock {
            Instant now=NOW;
            public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId zone){return this;} public Instant instant(){return now;}
        }
        var clock=new MutableClock(); var store=new LocalSourceAssetStorageAdapter(root,policy(10485760,25000000),clock);
        var asset=SourceAsset.pending(UUID.randomUUID(),NOW); var auth=store.createUploadAuthorization(asset.storageKey());
        clock.now=NOW.plusSeconds(901);
        assertThatThrownBy(() -> store.upload(token(auth.uploadUrl()),"image/png",new ByteArrayInputStream(fixture("png"))))
                .isInstanceOf(SourceException.class).hasMessage("SOURCE_UPLOAD_EXPIRED");
        Files.createSymbolicLink(root.resolve("source-items"),root.getParent());
        assertThatThrownBy(() -> store.createUploadAuthorization(asset.storageKey())).isInstanceOf(SourceException.class);
    }
    @Test void uploadSizeMimeMismatchAndCorruptImagesAreRejected() throws Exception {
        var limited=new LocalSourceAssetStorageAdapter(root.resolve("limited"),policy(20,25000000),Clock.fixed(NOW,ZoneOffset.UTC));
        assertThatThrownBy(() -> upload(limited,SourceAsset.pending(UUID.randomUUID(),NOW),"image/png",fixture("png")))
                .isInstanceOf(SourceException.class).hasMessage("SOURCE_IMAGE_TOO_LARGE");
        var policy=policy(10485760,25000000); var store=new LocalSourceAssetStorageAdapter(root.resolve("normal"),policy,Clock.fixed(NOW,ZoneOffset.UTC));
        var verifier=new StoredImageVerifier(store,policy,1);
        var mismatch=SourceAsset.pending(UUID.randomUUID(),NOW); upload(store,mismatch,"image/jpeg",fixture("png"));
        assertThatThrownBy(() -> verifier.verify(mismatch,NOW)).hasMessage("SOURCE_IMAGE_TYPE_UNSUPPORTED");
        var html=SourceAsset.pending(UUID.randomUUID(),NOW); upload(store,html,"image/png","<html>Not an image</html>".getBytes());
        assertThatThrownBy(() -> verifier.verify(html,NOW)).hasMessage("SOURCE_IMAGE_TYPE_UNSUPPORTED");
        var corrupt=SourceAsset.pending(UUID.randomUUID(),NOW); upload(store,corrupt,"image/png",Arrays.copyOf(fixture("png"),25));
        assertThatThrownBy(() -> verifier.verify(corrupt,NOW)).hasMessage("SOURCE_IMAGE_INVALID");
        assertThatThrownBy(() -> upload(store,SourceAsset.pending(UUID.randomUUID(),NOW),"image/svg+xml","<svg/>".getBytes())).hasMessage("SOURCE_IMAGE_TYPE_UNSUPPORTED");
        var pixels=SourceAsset.pending(UUID.randomUUID(),NOW); upload(store,pixels,"image/png",fixture("png"));
        assertThatThrownBy(() -> new StoredImageVerifier(store,policy(10485760,5),1).verify(pixels,NOW)).hasMessage("SOURCE_IMAGE_TOO_LARGE");
    }
    @Test void verificationBoundsMetadataAndActualContentIndependently() throws Exception {
        byte[] bytes=fixture("png");
        for(long size:new long[]{10485761,1,0}) {
            SourceAssetStorage fake=new SourceAssetStorage() {
                public UploadAuthorization createUploadAuthorization(String key){throw new UnsupportedOperationException();}
                public ObjectMetadata headObject(String key){return new ObjectMetadata(size,"image/png");}
                public InputStream openObject(String key){return new ByteArrayInputStream(bytes);}
                public void deleteObject(String key){} public String createTemporaryReadUrl(String key){throw new UnsupportedOperationException();}
            };
            assertThatThrownBy(() -> new StoredImageVerifier(fake,policy(10485760,25000000),1).verify(SourceAsset.pending(UUID.randomUUID(),NOW),NOW)).isInstanceOf(SourceException.class);
        }
        var store=new LocalSourceAssetStorageAdapter(root,policy(10485760,25000000),Clock.fixed(NOW,ZoneOffset.UTC));
        var asset=SourceAsset.pending(UUID.randomUUID(),NOW); upload(store,asset,"image/png",bytes);
        assertThatThrownBy(() -> new StoredImageVerifier(store,policy(20,25000000),1).verify(asset,NOW)).hasMessage("SOURCE_IMAGE_TOO_LARGE");
    }
}
