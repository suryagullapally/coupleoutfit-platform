package com.coupleoutfit.storage.infrastructure;

import com.coupleoutfit.source.application.*;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
public class StorageConfiguration {
    @Bean SourceAssetStorage sourceAssetStorage(Environment environment, UploadPolicy policy, Clock sourceClock,
            @Value("${coupleoutfit.storage.provider:disabled}") String provider,
            @Value("${coupleoutfit.storage.local.root:.local/source-assets}") String root) {
        boolean development=environment.matchesProfiles("local | test") && !environment.matchesProfiles("staging | production");
        if(development && provider.equals("local")) return new LocalSourceAssetStorageAdapter(Path.of(root),policy,sourceClock);
        if(!provider.equals("disabled")) throw new IllegalStateException("Configured source storage provider is unavailable in this profile.");
        return new SourceAssetStorage() {
            private SourceException unavailable() { return new SourceException("SOURCE_STORAGE_UNAVAILABLE"); }
            public UploadAuthorization createUploadAuthorization(String key) { throw unavailable(); }
            public ObjectMetadata headObject(String key) { throw unavailable(); }
            public java.io.InputStream openObject(String key) { throw unavailable(); }
            public void deleteObject(String key) { throw unavailable(); }
            public String createTemporaryReadUrl(String key) { throw unavailable(); }
        };
    }
}
