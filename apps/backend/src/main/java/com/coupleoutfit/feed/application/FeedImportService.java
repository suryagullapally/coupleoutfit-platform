package com.coupleoutfit.feed.application;
import com.coupleoutfit.feed.domain.*;
import java.time.Clock;
import java.util.*;
/** Internal use case. Parsing/provider work never runs inside a catalogue transaction. */
public final class FeedImportService {
    private final FeedRunRepository runs;
    private final FeedContentProvider content;
    private final AffiliateFeedReader reader;
    private final FeedItemNormalizer normalizer;
    private final Clock clock;
    private final int batchSize,maxRejectedRows;
    public FeedImportService(FeedRunRepository runs,FeedContentProvider content,AffiliateFeedReader reader,FeedItemNormalizer normalizer,Clock clock,int batchSize,int maxRejectedRows) {
        this.runs=runs;this.content=content;this.reader=reader;this.normalizer=normalizer;this.clock=clock;
        if(batchSize<1 || batchSize>1000 || maxRejectedRows<0 || maxRejectedRows>100000)throw new IllegalArgumentException("Invalid feed limits.");
        this.batchSize=batchSize;this.maxRejectedRows=maxRejectedRows;
    }
    public AffiliateFeedRun run(UUID feedId) {
        var context=runs.start(feedId,clock.instant());var id=context.run().id();
        var batch=new ArrayList<FeedRowOutcome>(batchSize);boolean batchFailed=false;long rejected=0;
        try {
            runs.processing(id);
            try(var input=content.open(context.feed());var cursor=reader.open(input,context.feed().schema())) {
                for(var row=cursor.next();row.isPresent();row=cursor.next()) {
                    try {batch.add(new FeedRowOutcome(row.get().rowNumber(),normalizer.normalize(row.get(),context.market()),null));}
                    catch(FeedException error){batch.add(new FeedRowOutcome(row.get().rowNumber(),null,error.code()));rejected++;}
                    if(batch.size()==batchSize || rejected>maxRejectedRows) {
                        batchFailed=true;rejected=runs.applyBatch(context,List.copyOf(batch),clock.instant());batchFailed=false;batch.clear();
                    }
                    if(rejected>maxRejectedRows)throw new FeedException("FEED_TOO_MANY_REJECTIONS");
                }
                if(!batch.isEmpty()){batchFailed=true;rejected=runs.applyBatch(context,List.copyOf(batch),clock.instant());batchFailed=false;batch.clear();}
                if(rejected>maxRejectedRows)throw new FeedException("FEED_TOO_MANY_REJECTIONS");
            }
            return runs.complete(id,clock.instant());
        } catch(Exception error) {
            // Retain valid rows already read before a parser/read failure; never retry a failed batch.
            if(!batchFailed && !batch.isEmpty()) {
                try{runs.applyBatch(context,List.copyOf(batch),clock.instant());}catch(RuntimeException ignored){ /* Final failure still records the run. */ }
            }
            String code=error instanceof FeedException feed?feed.code():"FEED_RUN_FAILED";
            return runs.fail(id,code,clock.instant());
        }
    }
}
