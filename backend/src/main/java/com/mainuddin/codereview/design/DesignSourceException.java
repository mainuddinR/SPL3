package com.mainuddin.codereview.design;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only application-defined codes and numeric metadata; never remote bodies or source. */
public class DesignSourceException extends ResponseStatusException {
    private final String code;
    private final Long observed;
    private final Long limit;
    public DesignSourceException(String code, String message, Long observed, Long limit) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
        this.code = code; this.observed = observed; this.limit = limit;
    }
    public String code() { return code; }
    public Long observed() { return observed; }
    public Long limit() { return limit; }
    public String savedMessage() { return code + "|" + (observed == null ? "" : observed) + "|" + (limit == null ? "" : limit) + "|" + getReason(); }
}
