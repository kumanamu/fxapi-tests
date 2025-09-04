// com/fxapp/config/TwelveProps.java
package com.fxapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("twelve")
public class TwelveProps {
    private String baseUrl;
    private String apiKey;
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
