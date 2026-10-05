package com.mainuddin.codereview.service;

public interface SuggestionProviderClient {
    SuggestionDraft generate(String input);
    String modelIdentifier();
}
