package com.coupleoutfit.compatibility.infrastructure;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.errors.*;
import java.io.InterruptedIOException;
public final class OpenAiEmbeddingProvider implements EmbeddingProvider,AutoCloseable {
    private final OpenAIClient client;private final EmbeddingModelMetadata model;
    public OpenAiEmbeddingProvider(OpenAIClient client,EmbeddingModelMetadata model){this.client=client;this.model=model;}
    @Override public Result embed(EmbeddingDescriptor descriptor) {
        var params=EmbeddingCreateParams.builder().model(model.model()).dimensions(model.dimensions()).encodingFormat(EmbeddingCreateParams.EncodingFormat.FLOAT).input(descriptor.text()).build();
        try(var http=client.embeddings().withRawResponse().create(params)) {
            var response=http.parse();
            if(response.data().size()!=1 || response.data().getFirst().index()!=0 || !response.model().equals(model.model()))throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
            var values=response.data().getFirst().embedding();
            if(values.size()!=model.dimensions())throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
            String requestId=http.requestId().orElse(null);
            if(requestId!=null && !requestId.matches("[A-Za-z0-9_-]{1,200}"))requestId=null;
            return new Result(new EmbeddingVector(values),model,requestId);
        } catch(EmbeddingException e){throw e;}
        catch(OpenAIServiceException e) {
            if(e.statusCode()==429)throw new EmbeddingException("EMBEDDING_RATE_LIMITED",true);
            throw new EmbeddingException("EMBEDDING_PROVIDER_UNAVAILABLE",e.statusCode()>=500 || e.statusCode()==408);
        } catch(OpenAIIoException e) {
            boolean timeout=false;for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof InterruptedIOException || cause instanceof java.net.http.HttpTimeoutException)timeout=true;
            throw new EmbeddingException(timeout?"EMBEDDING_TIMEOUT":"EMBEDDING_PROVIDER_UNAVAILABLE",true);
        } catch(RuntimeException e){throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");}
    }
    @Override public void close(){client.close();}
}
