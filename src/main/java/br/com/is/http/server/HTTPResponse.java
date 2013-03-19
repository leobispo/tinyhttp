package br.com.is.http.server;

import java.util.Collection;

public class HTTPResponse {
  public void addCookie(Cookie cookie) {
    
  }
  
  public void addDateHeader(final String name, long date) {
    
  }
  
  public void addHeader(final String name, final String value) {
    
  }
  
  public void addIntHeader(final String name, int value) {
    
  }
  
  public boolean containsHeader(final String name) {
    return false;
  }
  
  public String encodeRedirectUrl(final String url) {
    return "";
  }
  
  public String encodeUrl(final String url) {
    return "";
  }
  
  public String getHeader(final String name) {
    return "";
  }
  
  public Collection<String> getHeaderNames() {
    return null;
  }
  
  public Collection<String> getHeaders(final String name) {
    return null;
  }
  
  public int getStatus() {
    return 0;
  }
  
  public void sendError(int sc) {
    
  }
  
  public void sendError(int sc, final String msg) {
    
  }
  
  public void sendRedirect(final String location) {
    
  }
  
  public void setDateHeader(final String name, long date) {
    
  }
  
  public void setHeader(final String name, final String value) {
    
  }
  
  public void setIntHeader(final String name, int value) {
    
  }
  
  public void setStatus(int sc) {
    
  }
}
