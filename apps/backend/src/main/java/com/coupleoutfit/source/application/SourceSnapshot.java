package com.coupleoutfit.source.application;

import com.coupleoutfit.source.domain.*;
public record SourceSnapshot(SourceItem item, SourceAsset asset, SourceUrl url) {}
