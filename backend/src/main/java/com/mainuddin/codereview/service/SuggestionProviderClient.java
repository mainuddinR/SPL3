package com.mainuddin.codereview.service;

public interface SuggestionProviderClient {
    SuggestionDraft generate(String input);
    default SuggestionDraft generateDesign(String input) {
        throw new UnsupportedOperationException("Design advice is not supported by this provider adapter");
    }
    String modelIdentifier();
}
