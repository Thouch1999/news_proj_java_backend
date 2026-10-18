package com.api.v1.bearerauth.model.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class SettingsUpdateRequest {
    // setting key -> new value (a null or empty value clears an optional setting); the keys must belong to the group in the URL
    @NotEmpty(message = "At least one setting is required")
    @Size(max = 50, message = "Too many settings in one request")
    private Map<String, String> values;
}
