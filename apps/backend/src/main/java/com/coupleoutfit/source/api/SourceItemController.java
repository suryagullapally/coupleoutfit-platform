package com.coupleoutfit.source.api;

import com.coupleoutfit.source.application.*;
import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/source-items")
public class SourceItemController {
    private final CreateSourceItem create;
    private final CompleteSourceUpload complete;
    private final SourceItemRepository repository;
    private final AnonymousSourceSession session;
    public SourceItemController(CreateSourceItem create,CompleteSourceUpload complete,SourceItemRepository repository,AnonymousSourceSession session) {
        this.create=create; this.complete=complete; this.repository=repository; this.session=session;
    }
    public record CreateRequest(SourceType sourceType,String url) {}
    public record CompleteRequest(UUID assetId) {}
    public record AssetResponse(UUID assetId,AssetStatus status,String mediaType,Integer width,Integer height) {}
    public record UrlResponse(UrlResolutionStatus status,UUID retailerMarketId,UUID productOfferId,String reason) {}
    public record UploadResponse(UUID assetId,String uploadUrl,java.time.Instant expiresAt,String method) {
        @Override public String toString() { return "UploadResponse[assetId="+assetId+", expiresAt="+expiresAt+"]"; }
    }
    public record Response(UUID sourceItemId,SourceType sourceType,SourceStatus status,MarketContext market,
            AssetResponse asset,UrlResponse urlResolution,UploadResponse upload,String action) {}
    private Response dto(SourceSnapshot snapshot,SourceAssetStorage.UploadAuthorization upload) {
        var s=snapshot.item(); var a=snapshot.asset(); var u=snapshot.url();
        return new Response(s.id(),s.sourceType(),s.status(),s.market(),a==null?null:new AssetResponse(a.id(),a.status(),a.mediaType(),a.width(),a.height()),
                u==null?null:new UrlResponse(u.resolutionStatus(),u.resolvedRetailerMarketId(),u.resolvedProductOfferId(),u.failureCode()),upload==null?null:new UploadResponse(a.id(),upload.uploadUrl(),upload.expiresAt(),upload.method()),
                s.status()==SourceStatus.IMAGE_UPLOAD_REQUIRED?"UPLOAD_IMAGE":null);
    }
    private ResponseEntity<Response> result(SourceSnapshot source,SourceAssetStorage.UploadAuthorization upload,HttpStatus status) {
        var item=source.item();
        LoggerFactory.getLogger(SourceItemController.class).atInfo().addKeyValue("sourceItemId",item.id()).addKeyValue("sourceType",item.sourceType())
                .addKeyValue("sourceStatus",item.status()).addKeyValue("assetId",source.asset()==null?null:source.asset().id())
                .addKeyValue("resolverType",source.url()==null?null:source.url().resolverType())
                .addKeyValue("resolutionStatus",source.url()==null?null:source.url().resolutionStatus()).log("Source state returned");
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(dto(source,upload));
    }
    @PostMapping public ResponseEntity<Response> create(@RequestBody CreateRequest body,HttpServletRequest request,HttpServletResponse response) {
        session.guardWrite(request);
        var created=create.create(session.createOrRead(request,response),body.sourceType(),body.url());
        return result(created.source(),created.upload(),HttpStatus.CREATED);
    }
    @PostMapping("/{id}/upload-authorization") public ResponseEntity<Response> fallback(@PathVariable UUID id,HttpServletRequest request) {
        session.guardWrite(request);
        var created=create.authorizeFallback(id,session.require(request));
        return result(created.source(),created.upload(),HttpStatus.OK);
    }
    @PostMapping("/{id}/upload-complete") public ResponseEntity<Response> complete(@PathVariable UUID id,@RequestBody CompleteRequest body,HttpServletRequest request) {
        session.guardWrite(request);
        if(body.assetId()==null) throw new SourceException("SOURCE_REQUEST_INVALID");
        return result(complete.complete(id,body.assetId(),session.require(request)),null,HttpStatus.OK);
    }
    @GetMapping("/{id}") public ResponseEntity<Response> get(@PathVariable UUID id,HttpServletRequest request) {
        return result(repository.findOwned(id,session.require(request)),null,HttpStatus.OK);
    }
}
