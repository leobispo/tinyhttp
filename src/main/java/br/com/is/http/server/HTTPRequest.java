package br.com.is.http.server;

import java.security.Principal;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;


public interface HTTPRequest {
  public enum RequestMethod { POST, GET, PUT, HEAD, DELETE, TRACE, CONNECT };
  
  public boolean authenticate(final HTTPResponse response);
  
  public String getAuthType();
  
  public String getContextPath();
  
  public List<Cookie> getCookies();
  
  public long getDateHeader(final String name);
  
  public String getHeader(final String name);
  
  public Enumeration<String> getHeaderNames();

  public RequestMethod getMethod();
  
  public Part getPart(final String name);
  
  public String getPathInfo();
  
  public String getPathTranslated();
  
  public String getQueryString();
  
  public String getRemoteUser();
  
  public String getRequestedSessionId();
  
  public String getRequestURI();
  
  public StringBuffer getRequestURL();
  
  public HTTPSession getSession();
  
  public HTTPSession getSession(boolean create);
  
  public Principal getUserPrincipal();
  
  boolean isRequestedSessionIdFromCookie();
  
  boolean isRequestedSessionIdFromURL();
  
  boolean isRequestedSessionIdValid();
  
  boolean isUserInRole(final String role);
  
  public void login(final String username, final String password);
  
  public void logout();
  
  public String getParameter(final String name);
  
  public Map<String, String> getParameterMap();
  
  public Enumeration<String>  getParameterNames();
}
