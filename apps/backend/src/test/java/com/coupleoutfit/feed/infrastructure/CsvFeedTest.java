package com.coupleoutfit.feed.infrastructure;
import com.coupleoutfit.affiliate.domain.*;
import com.coupleoutfit.feed.FeedFixtures;
import com.coupleoutfit.feed.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CsvFeedTest {
    @TempDir Path root;
    private final CsvAffiliateFeedReader reader=new CsvAffiliateFeedReader();
    private final FeedSchema schema=new FeedSchema(Map.of(FeedField.RETAILER_PRODUCT_ID,"id",FeedField.TITLE,"name",FeedField.PRODUCT_URL,"url",FeedField.CATEGORY,"category",FeedField.TARGET,"target"));
    private static ByteArrayInputStream bytes(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    private static final String HEADER="id,name,url,category,target\n";
    @Test void quotesUtf8BomUnknownColumnsAndMalformedRows() {
        try(var cursor=reader.open(bytes("\uFEFFid,name,url,category,target,ignored\n1,\"Café, shirt\",https://example.com/p,shirt,men,extra\n2,short\n3,\"Multi\nline\",https://example.com/p,shirt,men,extra\n"),schema)) {
            assertThat(cursor.next().orElseThrow().values().get(FeedField.TITLE)).isEqualTo("Café, shirt");
            assertThat(cursor.next().orElseThrow().validShape()).isFalse();
            assertThat(cursor.next().orElseThrow().values().get(FeedField.TITLE)).isEqualTo("Multi\nline");assertThat(cursor.next()).isEmpty();
        }
    }
    @Test void emptyMissingDuplicateHeadersAndBrokenQuotesFail() {
        for(String invalid:List.of("", "id,name\n1,test", "id,name,name,category,target\n"))assertThatThrownBy(()->reader.open(bytes(invalid),schema)).hasMessage("FEED_FORMAT_INVALID");
        try(var cursor=reader.open(bytes(HEADER+"1,\"unterminated"),schema)){assertThatThrownBy(cursor::next).hasMessage("FEED_FORMAT_INVALID");}
        try(var cursor=reader.open(bytes(HEADER),schema)){assertThat(cursor.next()).isEmpty();}
    }
    @Test void localRootByteBoundaryAndSymlinkEscape()throws Exception {
        var program=FeedFixtures.program(UUID.randomUUID(),AffiliateProgramStatus.ACTIVE);var feed=FeedFixtures.feed(program,"fixture.csv",schema);
        Files.writeString(root.resolve("fixture.csv"),"1234");
        try(var input=new LocalFileFeedContentProvider(root,4).open(feed)){assertThat(input.readAllBytes()).hasSize(4);}
        assertThatThrownBy(()->new LocalFileFeedContentProvider(root,3).open(feed)).hasMessage("FEED_TOO_LARGE");
        try(var input=LocalFileFeedContentProvider.bounded(bytes("12345"),4)){assertThatThrownBy(input::readAllBytes).hasMessage("FEED_TOO_LARGE");}
        Path outside=Files.createTempFile("co-feed-outside", ".csv");
        try {Files.createSymbolicLink(root.resolve("escape.csv"),outside);assertThatThrownBy(()->new LocalFileFeedContentProvider(root,100).open(FeedFixtures.feed(program,"escape.csv",schema))).hasMessage("FEED_DOWNLOAD_FAILED");}
        finally {Files.deleteIfExists(outside);}
    }
    @Test void parserReadsIncrementallyInsteadOfMaterializingTheFeed() {
        String row="1,shirt,https://example.com/p,shirt,men\n";
        var content=bytes(HEADER+row.repeat(10000));
        try(var cursor=reader.open(content,schema)){assertThat(cursor.next()).isPresent();assertThat(content.available()).isGreaterThan(300000);}
    }
    @Test void invalidUtf8FailsClearly() {
        byte[] prefix=HEADER.getBytes(StandardCharsets.UTF_8);byte[] all=Arrays.copyOf(prefix,prefix.length+2);all[prefix.length]=(byte)0xc3;all[prefix.length+1]=(byte)0x28;
        assertThatThrownBy(()->{try(var cursor=reader.open(new ByteArrayInputStream(all),schema)){cursor.next();}}).hasMessage("FEED_FORMAT_INVALID");
    }
}
