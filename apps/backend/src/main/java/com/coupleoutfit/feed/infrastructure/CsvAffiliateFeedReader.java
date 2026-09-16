package com.coupleoutfit.feed.infrastructure;
import com.coupleoutfit.affiliate.domain.*;
import com.coupleoutfit.feed.application.AffiliateFeedReader;
import com.coupleoutfit.feed.domain.*;
import org.apache.commons.csv.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
public final class CsvAffiliateFeedReader implements AffiliateFeedReader {
    @Override public Cursor open(InputStream content,FeedSchema schema) {
        try {
            var input=new PushbackReader(new InputStreamReader(content,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),1);
            int first=input.read();if(first!=-1 && first!=0xFEFF)input.unread(first);
            var format=CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).setIgnoreEmptyLines(false).get();
            var parser=CSVParser.builder().setReader(input).setFormat(format).get();
            if(parser.getHeaderNames().isEmpty() || parser.getHeaderNames().size()>64 || !parser.getHeaderNames().containsAll(schema.columns().values())) {
                parser.close();throw new FeedException("FEED_FORMAT_INVALID");
            }
            var records=parser.iterator();
            return new Cursor() {
                @Override public Optional<RawAffiliateFeedRow> next() {
                    try {
                        if(!records.hasNext())return Optional.empty();var record=records.next();
                        if(!record.isConsistent())return Optional.of(new RawAffiliateFeedRow(record.getRecordNumber(),Map.of(),false));
                        var values=new EnumMap<FeedField,String>(FeedField.class);boolean valid=true;
                        for(var column:schema.columns().entrySet()) {
                            String value=record.get(column.getValue());if(value.length()>4096)valid=false;
                            values.put(column.getKey(),value.length()>4096?"":value);
                        }
                        return Optional.of(new RawAffiliateFeedRow(record.getRecordNumber(),values,valid));
                    }catch(UncheckedIOException | IllegalArgumentException e){throw new FeedException("FEED_FORMAT_INVALID");}
                }
                @Override public void close(){try{parser.close();}catch(IOException e){throw new FeedException("FEED_DOWNLOAD_FAILED");}}
            };
        }catch(IOException | IllegalArgumentException e){throw new FeedException("FEED_FORMAT_INVALID");}
    }
}
