package com.azentio.clearlist.entity;

import com.fasterxml.jackson.annotation.JsonValue;

/** Which name of the watchlist entry produced the best score. Serialised as "primary" / "alias". */
public enum MatchedOn {
    PRIMARY,
    ALIAS;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
