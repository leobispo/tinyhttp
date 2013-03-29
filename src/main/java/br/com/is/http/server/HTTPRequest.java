/* Copyright (C) 2013 Leonardo Bispo de Oliveira
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package br.com.is.http.server;

import java.security.Principal;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

public interface HTTPRequest { 
  public enum RequestMethod { POST, GET, PUT, HEAD, DELETE, TRACE, OPTIONS, CONNECT };

  public enum AuthType { BASIC_AUTH, FORM_AUTH, CLIENT_CERT_AUTH, DIGEST_AUTH, NONE_AUTH };
  
  public boolean authenticate(final HTTPResponse response);
  
  public AuthType getAuthType();
  
  public List<Cookie> getCookies();
  
  public long getDateHeader(final String name);
  
  public String getHeader(final String name);
  
  public Enumeration<String> getHeaderNames();

  public RequestMethod getMethod();
  
  public Part getPart(final String name);
  
  public String getRemoteUser();
  
  public String getRequestedSessionId();
  
  public String getRequestURI();
  
  public StringBuffer getRequestURL();
  
  public HTTPSession getSession();
  
  public Principal getUserPrincipal();
  
  boolean isUserInRole(final String role);
  
  public void login(final String username, final String password);
  
  public void logout();
  
  public String getParameter(final String name);
  
  public Map<String, String> getParameterMap();
  
  public Enumeration<String>  getParameterNames();
}
