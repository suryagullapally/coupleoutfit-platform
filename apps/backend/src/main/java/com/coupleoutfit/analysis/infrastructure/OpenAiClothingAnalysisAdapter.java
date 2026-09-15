package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.application.*;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.*;
import com.openai.models.responses.*;
import java.util.*;
import java.io.InterruptedIOException;

/** The only provider adapter. SDK objects and provider errors stay inside infrastructure. */
public final class OpenAiClothingAnalysisAdapter implements ClothingAnalysisPort,AutoCloseable {
    private final OpenAIClient client;
    private final String model;
    private final String prompt;
    private final String promptVersion;
    private final ResponseFormatTextJsonSchemaConfig.Schema schema;
    private final ResponseInputImage.Detail detail;
    private final long maxOutputTokens;
    private final ClothingSchema parser=new ClothingSchema();
    public OpenAiClothingAnalysisAdapter(OpenAIClient client,String model,String prompt,String promptVersion,Map<String,Object> schema,String detail,long maxOutputTokens) {
        this.client=client;this.model=model;this.prompt=prompt;this.promptVersion=promptVersion;
        this.schema=JsonValue.from(schema).convert(ResponseFormatTextJsonSchemaConfig.Schema.class);
        this.detail=ResponseInputImage.Detail.of(detail);this.maxOutputTokens=maxOutputTokens;
    }
    @Override public ClothingAnalysisProviderResult analyse(AnalysisImage input) {
        String imageUrl=input.trustedImageUrl();
        if(imageUrl==null) imageUrl="data:"+input.mediaType()+";base64,"+Base64.getEncoder().encodeToString(input.bytes());
        var params=ResponseCreateParams.builder().model(model).store(false).instructions(prompt).maxOutputTokens(maxOutputTokens)
                .inputOfResponse(List.of(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder().role(EasyInputMessage.Role.USER)
                    .contentOfResponseInputMessageContentList(List.of(ResponseInputContent.ofInputImage(ResponseInputImage.builder().imageUrl(imageUrl).detail(detail).build()))).build())))
                .text(ResponseTextConfig.builder().format(ResponseFormatTextJsonSchemaConfig.builder().name("clothing_analysis_v1").strict(true).schema(schema).build()).build()).build();
        try(var http=client.responses().withRawResponse().create(params)) {
            var response=http.parse();
            if(!ResponseStatus.COMPLETED.equals(response.status().orElse(null)) || response.error().isPresent()) throw new AnalysisException("ANALYSIS_INVALID_RESPONSE");
            var texts=new ArrayList<String>();
            for(var item:response.output()) {
                if(item.isMessage()) for(var part:item.asMessage().content()) {
                    if(part.isRefusal()) throw new AnalysisException("ANALYSIS_IMAGE_UNSUPPORTED");
                    if(part.isOutputText()) texts.add(part.asOutputText().text());
                }
            }
            if(texts.size()!=1) throw new AnalysisException("ANALYSIS_INVALID_RESPONSE");
            var parsed=parser.parse(texts.getFirst());
            String requestId=http.requestId().orElse(response.id());
            if(!requestId.matches("[A-Za-z0-9_-]{1,200}")) requestId=null;
            return new ClothingAnalysisProviderResult(parsed.attributes(),parsed.confidence(),"OPENAI",model,requestId,promptVersion);
        } catch(AnalysisException error) { throw error; }
        catch(OpenAIServiceException error) {
            int status=error.statusCode();
            if(status==429) throw new AnalysisException("ANALYSIS_PROVIDER_RATE_LIMITED",true);
            if(status>=500 || status==408) throw new AnalysisException("ANALYSIS_PROVIDER_UNAVAILABLE",true);
            if(status==400 || status==422) throw new AnalysisException("ANALYSIS_IMAGE_UNSUPPORTED");
            throw new AnalysisException("ANALYSIS_PROVIDER_UNAVAILABLE");
        } catch(OpenAIIoException error) {
            boolean timeout=false;
            for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof InterruptedIOException || cause instanceof java.net.http.HttpTimeoutException) timeout=true;
            throw new AnalysisException(timeout?"ANALYSIS_TIMEOUT":"ANALYSIS_PROVIDER_UNAVAILABLE",true);
        } catch(RuntimeException error) { throw new AnalysisException("ANALYSIS_INVALID_RESPONSE"); }
    }
    @Override public void close() { client.close(); }
}
