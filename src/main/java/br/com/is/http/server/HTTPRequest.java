package br.com.is.http.server;

import java.security.Principal;
import java.util.Enumeration;
import java.util.List;


public class HTTPRequest {
  
  public boolean authenticate(final HTTPResponse response) {
    return false;
  }
  
  public String getAuthType() {
    return "";
  }
  
  public String getContextPath() {
    return "";
  }
  
  List<Cookie> getCookies() {
    return null;
  }
  
  public long getDateHeader(final String name) {
    return 0;
  }
  
  public String getHeader(final String name) {
    return "";
  }
  
  public Enumeration<String> getHeaderNames() {
    return null;
  }
  
  public Enumeration<String> getHeaders(final String name)  {
    return null;
  }
  
  public int getIntHeader(final String name) {
    return 0;
  }

  public String getMethod() {
    return "";
  }
  
  public Part getPart(final String name) {
    return null;
  }
  
  public String getPathInfo() {
    return "";
  }
  
  public String getPathTranslated() {
    return "";
  }
  
  public String getQueryString() {
    return "";
  }
  
  public String getRemoteUser() {
    return "";
  }
  
  public String getRequestedSessionId() {
    return "";
  }
  
  public String getRequestURI() {
    return "";
  }
  
  public StringBuffer getRequestURL() {
    return null;
  }
  
  HTTPSession getSession() {
    return null;
  }
  
  public HTTPSession getSession(boolean create) {
    return null;
  }
  
  public Principal getUserPrincipal() {
    return null;
  }
  
  boolean isRequestedSessionIdFromCookie() {
    return false;
  }
  
  boolean isRequestedSessionIdFromURL() {
    return false;
  }
  
  boolean isRequestedSessionIdValid() {
    return false;
  }
  
  boolean isUserInRole(final String role) {
    return false;
  }
  
  public void login(final String username, final String password) {
  }
  
  public void logout() {
  }
}
