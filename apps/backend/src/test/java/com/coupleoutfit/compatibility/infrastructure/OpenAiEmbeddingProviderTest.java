package com.coupleoutfit.compatibility.infrastructure;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.http.HttpResponseFor;
import com.openai.errors.*;
import com.openai.models.embeddings.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.time.Duration;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class OpenAiEmbeddingProviderTest {
    OpenAIClient client;HttpResponseFor<CreateEmbeddingResponse> http;OpenAiEmbeddingProvider provider;
    EmbeddingModelMetadata model=new EmbeddingModelMetadata("OPENAI","text-embedding-3-small",1536);
    EmbeddingDescriptor descriptor=new EmbeddingDescriptor("compatibility-descriptor-v1","descriptor: compatibility-descriptor-v1\ncategory: SHIRT\n");
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        client=mock(OpenAIClient.class,RETURNS_DEEP_STUBS);http=mock(HttpResponseFor.class);
        when(client.embeddings().withRawResponse().create(any(EmbeddingCreateParams.class))).thenReturn(http);
        when(http.requestId()).thenReturn(Optional.of("req_embedding"));provider=new OpenAiEmbeddingProvider(client,model);
    }
    private void response(List<Float> vector,String returnedModel) {
        var value=JsonValue.from(Map.of("data",List.of(Map.of("index",0,"object","embedding","embedding",vector)),"model",returnedModel,"object","list")).convert(CreateEmbeddingResponse.class);
        when(http.parse()).thenReturn(value);
    }
    @Test void requestAndResponseUseConfiguredModelDimensionsAndTextOnly() {
        var vector=new ArrayList<>(Collections.nCopies(1536,0f));vector.set(0,1f);response(vector,model.model());
        var result=provider.embed(descriptor);assertThat(result.model()).isEqualTo(model);assertThat(result.providerRequestId()).isEqualTo("req_embedding");
        assertThat(result.vector().values()).containsExactlyElementsOf(vector);
        var captured=ArgumentCaptor.forClass(EmbeddingCreateParams.class);verify(client.embeddings().withRawResponse()).create(captured.capture());
        var p=captured.getValue();assertThat(p.model().asString()).isEqualTo(model.model());assertThat(p.dimensions()).contains(1536L);
        assertThat(p.input().asString()).isEqualTo(descriptor.text());assertThat(p.toString()).doesNotContain("image","affiliate","currency","ownerSession");verify(http).close();
    }
    @Test void invalidDimensionModelAndZeroVectorAreRejected() {
        response(List.of(1f),model.model());assertThatThrownBy(()->provider.embed(descriptor)).hasMessage("EMBEDDING_DIMENSION_MISMATCH");
        response(Collections.nCopies(1536,0f),model.model());assertThatThrownBy(()->provider.embed(descriptor)).hasMessage("EMBEDDING_INVALID_RESPONSE");
        response(List.of(1f),"wrong-model");assertThatThrownBy(()->provider.embed(descriptor)).hasMessage("EMBEDDING_INVALID_RESPONSE");
    }
    @Test void rateLimitsServerFailuresAndAuthenticationAreSafe() {
        var service=client.embeddings().withRawResponse();
        for(int status:List.of(429,500,401)) {
            var failure=mock(OpenAIServiceException.class);when(failure.statusCode()).thenReturn(status);doThrow(failure).when(service).create(any(EmbeddingCreateParams.class));
            assertThatThrownBy(()->provider.embed(descriptor)).isInstanceOfSatisfying(EmbeddingException.class,e->{
                assertThat(e.code()).isEqualTo(status==429?"EMBEDDING_RATE_LIMITED":"EMBEDDING_PROVIDER_UNAVAILABLE");assertThat(e.transientFailure()).isEqualTo(status!=401);assertThat(e.getCause()).isNull();});
        }
    }
    @Test void timeoutIsTransientAndMissingKeyDisablesOnlyProvider() {
        when(client.embeddings().withRawResponse().create(any(EmbeddingCreateParams.class))).thenThrow(new OpenAIIoException("private account",new SocketTimeoutException()));
        assertThatThrownBy(()->provider.embed(descriptor)).isInstanceOfSatisfying(EmbeddingException.class,e->{assertThat(e.code()).isEqualTo("EMBEDDING_TIMEOUT");assertThat(e.transientFailure()).isTrue();});
        var disabled=new EmbeddingConfiguration().embeddingProvider(model,"openai","",Duration.ofSeconds(5),Duration.ofSeconds(20));
        assertThatThrownBy(()->disabled.embed(descriptor)).hasMessage("EMBEDDING_PROVIDER_UNAVAILABLE");
        assertThatThrownBy(()->new EmbeddingConfiguration().embeddingModel(model.model(),3072)).isInstanceOf(IllegalArgumentException.class);
    }
}
