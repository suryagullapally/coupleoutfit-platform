package com.coupleoutfit.storage.infrastructure;

import com.coupleoutfit.storage.application.SourceAssetStorage;
import com.coupleoutfit.source.application.SourceException;
import com.coupleoutfit.source.application.UploadPolicy;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Local/test only. Single-process, write-once capabilities; objects never have public paths. */
public final class LocalSourceAssetStorageAdapter implements SourceAssetStorage {
    private record Capability(String key, Instant expiresAt) {}
    private final Path root;
    private final UploadPolicy policy;
    private final Clock clock;
    private final Map<String,Capability> uploads=new ConcurrentHashMap<>(), reads=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    public LocalSourceAssetStorageAdapter(Path root, UploadPolicy policy, Clock clock) {
        try {
            this.root=root.toAbsolutePath().normalize();
            Files.createDirectories(this.root);
            if(Files.isSymbolicLink(this.root)) throw new IOException();
        } catch(IOException e) { throw new IllegalStateException("Local storage is unavailable."); }
        this.policy=policy; this.clock=clock;
    }
    private Path path(String key) {
        if(key==null || !key.matches("source-items/[a-f0-9-]{36}/[a-f0-9-]{36}/original")) throw new SourceException("SOURCE_ASSET_INVALID");
        Path result=root.resolve(key).normalize();
        if(!result.startsWith(root)) throw new SourceException("SOURCE_ASSET_INVALID");
        for(Path p=result; p!=null && p.startsWith(root); p=p.getParent()) {
            if(Files.isSymbolicLink(p)) throw new SourceException("SOURCE_ASSET_INVALID");
        }
        return result;
    }
    private String authorize(Map<String,Capability> capabilities, String key) {
        path(key);
        capabilities.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(clock.instant()));
        if(capabilities.size()>=10000) throw new SourceException("SOURCE_STORAGE_UNAVAILABLE");
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        capabilities.put(token,new Capability(key,clock.instant().plus(policy.authorizationTtl())));
        return token;
    }
    @Override public UploadAuthorization createUploadAuthorization(String key) {
        String token=authorize(uploads,key);
        return new UploadAuthorization("/internal/local-upload?token="+token,uploads.get(token).expiresAt(),"PUT");
    }
    private Capability capability(Map<String,Capability> capabilities, String token) {
        Capability value=token==null?null:capabilities.get(token);
        if(value==null) throw new SourceException("SOURCE_UPLOAD_NOT_FOUND");
        if(!value.expiresAt().isAfter(clock.instant())) { capabilities.remove(token); throw new SourceException("SOURCE_UPLOAD_EXPIRED"); }
        return value;
    }
    public synchronized void upload(String token, String mediaType, InputStream input) {
        var auth=capability(uploads,token);
        if(!policy.allowedMediaTypes().contains(mediaType==null?"":mediaType)) throw new SourceException("SOURCE_IMAGE_TYPE_UNSUPPORTED");
        Path target=path(auth.key()), temp=null;
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) throw new SourceException("SOURCE_INVALID_STATE");
        try {
            Files.createDirectories(target.getParent()); path(auth.key());
            temp=Files.createTempFile(target.getParent(),"pending-",".tmp");
            long count=0;
            try(var out=Files.newOutputStream(temp)) {
                byte[] buffer=new byte[8192]; int n;
                while((n=input.read(buffer))!=-1) {
                    count+=n;
                    if(count>policy.maxBytes()) throw new SourceException("SOURCE_IMAGE_TOO_LARGE");
                    out.write(buffer,0,n);
                }
            }
            if(count==0) throw new SourceException("SOURCE_IMAGE_INVALID");
            if(!auth.expiresAt().isAfter(clock.instant())) throw new SourceException("SOURCE_UPLOAD_EXPIRED");
            Files.writeString(target.resolveSibling("media-type"),mediaType,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,LinkOption.NOFOLLOW_LINKS);
            Files.move(temp,target); // No overwrite: a key has exactly one immutable original.
            uploads.entrySet().removeIf(e -> e.getValue().key().equals(auth.key()));
        } catch(SourceException e) { throw e; }
        catch(IOException e) { throw new SourceException("SOURCE_STORAGE_UNAVAILABLE"); }
        finally { if(temp!=null) try { Files.deleteIfExists(temp); } catch(IOException ignored) {} }
    }
    @Override public ObjectMetadata headObject(String key) {
        Path target=path(key);
        try {
            if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)) throw new SourceException("SOURCE_UPLOAD_NOT_FOUND");
            Path metadata=target.resolveSibling("media-type");
            if(Files.isSymbolicLink(metadata)) throw new SourceException("SOURCE_ASSET_INVALID");
            return new ObjectMetadata(Files.size(target),Files.readString(metadata));
        } catch(IOException e) { throw new SourceException("SOURCE_STORAGE_UNAVAILABLE"); }
    }
    @Override public InputStream openObject(String key) {
        try { return Files.newInputStream(path(key),LinkOption.NOFOLLOW_LINKS); }
        catch(IOException e) { throw new SourceException("SOURCE_UPLOAD_NOT_FOUND"); }
    }
    @Override public synchronized void deleteObject(String key) {
        uploads.entrySet().removeIf(e -> e.getValue().key().equals(key)); reads.entrySet().removeIf(e -> e.getValue().key().equals(key));
        Path target=path(key);
        try { Files.deleteIfExists(target); Files.deleteIfExists(target.resolveSibling("media-type")); }
        catch(IOException e) { throw new SourceException("SOURCE_STORAGE_UNAVAILABLE"); }
    }
    @Override public String createTemporaryReadUrl(String key) {
        headObject(key);
        return "/internal/local-read?token="+authorize(reads,key);
    }
    public ObjectMetadata readMetadata(String token) { return headObject(capability(reads,token).key()); }
    public InputStream readAuthorized(String token) { return openObject(capability(reads,token).key()); }
}
