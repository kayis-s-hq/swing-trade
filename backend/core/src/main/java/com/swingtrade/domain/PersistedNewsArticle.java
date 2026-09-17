package com.swingtrade.domain;

import java.time.OffsetDateTime;

/** A news article together with its database identity and first-seen provenance. */
public record PersistedNewsArticle(
    long id,
    NewsArticle article,
    OffsetDateTime firstSeenAt
) {}
