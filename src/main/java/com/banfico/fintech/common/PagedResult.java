package com.banfico.fintech.common;

import java.util.List;

/** Generic pagination wrapper for our own API — applied in-memory over sandbox data we already fetched. */
public record PagedResult<T>(List<T> content, int page, int size, long totalElements) {
}
