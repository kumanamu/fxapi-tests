
package com.fxapp;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="app")
public class AppProps {
    private String liveBaseUrl;
    private String ecbBaseUrl;
    private int cacheTtlSeconds = 60;
    public String getLiveBaseUrl(){ return liveBaseUrl; }
    public void setLiveBaseUrl(String s){ this.liveBaseUrl = s; }
    public String getEcbBaseUrl(){ return ecbBaseUrl; }
    public void setEcbBaseUrl(String s){ this.ecbBaseUrl = s; }
    public int getCacheTtlSeconds(){ return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int i){ this.cacheTtlSeconds = i; }
}
