package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.AnalysisFixtures;
import com.coupleoutfit.analysis.application.*;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.http.HttpResponseFor;
import com.openai.errors.*;
import com.openai.models.responses.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiAdapterTest {
    OpenAIClient client;
    HttpResponseFor<Response> http;
    OpenAiClothingAnalysisAdapter adapter;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        client=mock(OpenAIClient.class,RETURNS_DEEP_STUBS);http=mock(HttpResponseFor.class);
        when(client.responses().withRawResponse().create(any(ResponseCreateParams.class))).thenReturn(http);
        when(http.requestId()).thenReturn(Optional.of("req_contract"));
        Map<String,Object> schema;
        try(var resource=getClass().getResourceAsStream("/analysis/clothing-analysis-schema-v1.json")) {
            schema=JsonMapper.builder().build().readValue(resource,new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
        }
        adapter=new OpenAiClothingAnalysisAdapter(client,"configured-vision","clothing only","clothing-analysis-v1",schema,"low",2048);
    }
    private void response(String text,boolean refusal) {
        var content=refusal?Map.of("type","refusal","refusal","safe refusal"):Map.of("type","output_text","text",text,"annotations",List.of());
        var message=Map.of("type","message","id","msg_test","role","assistant","status","completed","content",List.of(content));
        var raw=new HashMap<String,Object>();raw.put("id","resp_test");raw.put("status","completed");raw.put("error",null);raw.put("output",List.of(message));
        when(http.parse()).thenReturn(JsonValue.from(raw).convert(Response.class));
    }
    @Test void responsesRequestUsesConfiguredModelPrivateImageAndStrictSchema() {
        response(AnalysisFixtures.OUTPUT,false);
        var result=adapter.analyse(new AnalysisImage(new byte[]{1,2,3},"image/png",null));
        assertThat(result.model()).isEqualTo("configured-vision");assertThat(result.providerRequestId()).isEqualTo("req_contract");
        assertThat(result.attributes().primaryColors()).containsExactly("BURGUNDY");
        var captured=ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(client.responses().withRawResponse()).create(captured.capture());var p=captured.getValue();
        assertThat(p.model().orElseThrow().asString()).isEqualTo("configured-vision");assertThat(p.store()).contains(false);
        assertThat(p.text().orElseThrow().format().orElseThrow().asJsonSchema().strict()).contains(true);
        String request=p.toString();assertThat(request).contains("data:image/png;base64,AQID","low").doesNotContain("affiliate","countryCode","currencyCode");
        verify(http).close();
    }
    @Test void trustedResolvedUrlIsPassedWithoutFetchingOrAddingCommercialMetadata() {
        response(AnalysisFixtures.OUTPUT,false);adapter.analyse(new AnalysisImage(null,null,"https://shop.example.com/item.png"));
        var captured=ArgumentCaptor.forClass(ResponseCreateParams.class);verify(client.responses().withRawResponse()).create(captured.capture());
        assertThat(captured.getValue().toString()).contains("https://shop.example.com/item.png").doesNotContain("data:image");
    }
    @Test void malformedMissingAndInvalidEnumsNeverEscapeAsDomainData() {
        for(String output:List.of("```json {} ```",AnalysisFixtures.OUTPUT.replace("\"pattern\":\"SOLID\",",""),AnalysisFixtures.OUTPUT.replace("SOLID","INVALID"))) {
            response(output,false);assertThatThrownBy(() -> adapter.analyse(new AnalysisImage(new byte[]{1},"image/png",null)))
                    .isInstanceOfSatisfying(AnalysisException.class,e -> {assertThat(e.code()).isEqualTo("ANALYSIS_INVALID_RESPONSE");assertThat(e.transientFailure()).isFalse();});
        }
    }
    @Test void refusalIsPermanentAndSafe() {
        response("",true);assertThatThrownBy(() -> adapter.analyse(new AnalysisImage(new byte[]{1},"image/png",null)))
                .isInstanceOfSatisfying(AnalysisException.class,e -> {assertThat(e.code()).isEqualTo("ANALYSIS_IMAGE_UNSUPPORTED");assertThat(e.transientFailure()).isFalse();});
    }
    @Test void serviceErrorsAreTranslatedWithoutAccountDetails() {
        for(int status:List.of(429,500,503,400,401)) {
            var error=mock(OpenAIServiceException.class);when(error.statusCode()).thenReturn(status);
            var service=client.responses().withRawResponse();doThrow(error).when(service).create(any(ResponseCreateParams.class));
            assertThatThrownBy(() -> adapter.analyse(new AnalysisImage(new byte[]{1},"image/png",null)))
                    .isInstanceOfSatisfying(AnalysisException.class,e -> {
                        assertThat(e.transientFailure()).isEqualTo(status==429 || status>=500);
                        assertThat(e.code()).isEqualTo(status==429?"ANALYSIS_PROVIDER_RATE_LIMITED":status==400?"ANALYSIS_IMAGE_UNSUPPORTED":"ANALYSIS_PROVIDER_UNAVAILABLE");
                        assertThat(e.getCause()).isNull();
                    });
        }
    }
    @Test void timeoutIsTransientAndNoSdkCauseLeaks() {
        when(client.responses().withRawResponse().create(any(ResponseCreateParams.class))).thenThrow(new OpenAIIoException("private details",new SocketTimeoutException("private URL")));
        assertThatThrownBy(() -> adapter.analyse(new AnalysisImage(new byte[]{1},"image/png",null)))
                .isInstanceOfSatisfying(AnalysisException.class,e -> {assertThat(e.code()).isEqualTo("ANALYSIS_TIMEOUT");assertThat(e.transientFailure()).isTrue();assertThat(e.getCause()).isNull();});
    }
    @Test void missingCredentialsDisableOnlyAnalysis() throws Exception {
        var port=new AnalysisConfiguration().clothingAnalysisProvider("openai","","",Duration.ofSeconds(5),Duration.ofSeconds(20),"low",2048);
        assertThatThrownBy(() -> port.analyse(new AnalysisImage(new byte[]{1},"image/png",null))).hasMessage("ANALYSIS_PROVIDER_UNAVAILABLE");
    }
}
