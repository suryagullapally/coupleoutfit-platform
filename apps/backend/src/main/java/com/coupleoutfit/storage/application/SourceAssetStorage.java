package com.coupleoutfit.storage.application;

import java.io.*;
import java.time.*;

public interface SourceAssetStorage {
    record UploadAuthorization(String uploadUrl, Instant expiresAt, String method) {
        @Override public String toString() { return "UploadAuthorization[expiresAt="+expiresAt+"]"; }
    }
    record ObjectMetadata(long byteSize, String mediaType) {}
    UploadAuthorization createUploadAuthorization(String key);
    ObjectMetadata headObject(String key);
    InputStream openObject(String key);
    void deleteObject(String key);
    String createTemporaryReadUrl(String key);
}
