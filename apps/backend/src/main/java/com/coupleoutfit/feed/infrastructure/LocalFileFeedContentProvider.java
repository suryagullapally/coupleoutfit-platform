package com.coupleoutfit.feed.infrastructure;
import com.coupleoutfit.affiliate.domain.AffiliateFeed;
import com.coupleoutfit.feed.application.FeedContentProvider;
import com.coupleoutfit.feed.domain.FeedException;
import java.io.*;
import java.nio.file.*;
/** Configured dev/test root only. No URL downloader or arbitrary absolute path access. */
public final class LocalFileFeedContentProvider implements FeedContentProvider {
    private final Path root;private final long maxBytes;
    public LocalFileFeedContentProvider(Path root,long maxBytes) {
        this.root=root.toAbsolutePath().normalize();this.maxBytes=maxBytes;
        if(maxBytes<1 || maxBytes>1024L*1024*1024)throw new IllegalArgumentException("Invalid feed byte limit.");
    }
    @Override public InputStream open(AffiliateFeed feed) {
        try {
            Path base=root.toRealPath(),file=base.resolve(feed.sourceLocation()).normalize().toRealPath();
            if(!file.startsWith(base) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new FeedException("FEED_DOWNLOAD_FAILED");
            if(Files.size(file)>maxBytes)throw new FeedException("FEED_TOO_LARGE");
            return bounded(Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS),maxBytes);
        }catch(IOException e){throw new FeedException("FEED_DOWNLOAD_FAILED");}
    }
    static InputStream bounded(InputStream source,long limit) {
        return new FilterInputStream(source) {
            long count;
            private void counted(int n){if(n>0 && (count+=n)>limit)throw new FeedException("FEED_TOO_LARGE");}
            @Override public int read()throws IOException{int b=in.read();if(b!=-1)counted(1);return b;}
            @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,(int)Math.min(len,Math.max(1,limit-count+1)));counted(n);return n;}
        };
    }
}
