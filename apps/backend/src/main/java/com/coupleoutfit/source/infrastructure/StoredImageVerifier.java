package com.coupleoutfit.source.infrastructure;

import com.coupleoutfit.source.application.*;
import com.coupleoutfit.source.domain.SourceAsset;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;

public final class StoredImageVerifier implements ImageVerifier {
    private final SourceAssetStorage storage;
    private final UploadPolicy policy;
    private final Semaphore decoders;
    public StoredImageVerifier(SourceAssetStorage storage, UploadPolicy policy, int maxConcurrent) {
        this.storage=storage; this.policy=policy;
        if(maxConcurrent<1 || maxConcurrent>8) throw new IllegalArgumentException("Invalid image verification concurrency.");
        decoders=new Semaphore(maxConcurrent);
    }
    @Override public SourceAsset verify(SourceAsset asset, Instant now) {
        if(!decoders.tryAcquire()) throw new SourceException("SOURCE_VERIFICATION_BUSY");
        try { return inspect(asset,now); } finally { decoders.release(); }
    }
    private SourceAsset inspect(SourceAsset asset, Instant now) {
        var metadata=storage.headObject(asset.storageKey());
        if(metadata.byteSize()>policy.maxBytes()) throw new SourceException("SOURCE_IMAGE_TOO_LARGE");
        if(metadata.byteSize()<=0) throw new SourceException("SOURCE_IMAGE_INVALID");
        if(!policy.allowedMediaTypes().contains(metadata.mediaType())) throw new SourceException("SOURCE_IMAGE_TYPE_UNSUPPORTED");
        try(var input=storage.openObject(asset.storageKey())) {
            byte[] bytes=input.readNBytes(Math.toIntExact(policy.maxBytes()+1));
            if(bytes.length>policy.maxBytes()) throw new SourceException("SOURCE_IMAGE_TOO_LARGE");
            if(bytes.length!=metadata.byteSize()) throw new SourceException("SOURCE_IMAGE_INVALID");
            String type=signature(bytes);
            if(!type.equals(metadata.mediaType())) throw new SourceException("SOURCE_IMAGE_TYPE_UNSUPPORTED");
            try(var imageInput=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(imageInput);
                if(!readers.hasNext()) throw new SourceException("SOURCE_IMAGE_INVALID");
                var reader=readers.next();
                try {
                    reader.addIIOReadWarningListener((r,warning) -> { throw new SourceException("SOURCE_IMAGE_INVALID"); });
                    reader.setInput(imageInput,true,true);
                    int width=reader.getWidth(0), height=reader.getHeight(0);
                    if(width<=0 || height<=0 || width>policy.maxDimension() || height>policy.maxDimension() || (long)width*height>policy.maxPixels()) {
                        throw new SourceException("SOURCE_IMAGE_TOO_LARGE");
                    }
                    var decoded=reader.read(0);
                    if(decoded==null || decoded.getWidth()!=width || decoded.getHeight()!=height) throw new SourceException("SOURCE_IMAGE_INVALID");
                    decoded.flush();
                    String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                    return asset.verified(type,bytes.length,width,height,digest,now);
                } finally { reader.dispose(); }
            }
        } catch(SourceException e) { throw e; }
        catch(Exception e) { throw new SourceException("SOURCE_IMAGE_INVALID"); }
    }
    private static String signature(byte[] b) {
        if(b.length>=4 && (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255) {
            if((b[b.length-2]&255)!=255 || (b[b.length-1]&255)!=217) throw new SourceException("SOURCE_IMAGE_INVALID");
            return "image/jpeg";
        }
        if(b.length>=20 && java.util.Arrays.equals(java.util.Arrays.copyOf(b,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) {
            if(!new String(b,b.length-8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("IEND")) throw new SourceException("SOURCE_IMAGE_INVALID");
            return "image/png";
        }
        if(b.length>=20 && new String(b,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
            long length=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(b,4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt());
            if(length+8!=b.length) throw new SourceException("SOURCE_IMAGE_INVALID");
            return "image/webp";
        }
        throw new SourceException("SOURCE_IMAGE_TYPE_UNSUPPORTED");
    }
}
