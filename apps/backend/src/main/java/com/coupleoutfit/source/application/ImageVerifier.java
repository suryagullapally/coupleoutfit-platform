package com.coupleoutfit.source.application;

import com.coupleoutfit.source.domain.SourceAsset;
import java.time.Instant;
public interface ImageVerifier { SourceAsset verify(SourceAsset asset, Instant now); }
