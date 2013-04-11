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

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Creates a cookie, a small amount of information sent by a servlet to a Web browser, saved by the browser, and later sent back to the server. 
 * A cookie's value can uniquely identify a client, so cookies are commonly used for session management. A cookie has a name, a single value, 
 * and optional attributes such as a comment, path and domain qualifiers, a maximum age, and a version number. Some Web browsers have bugs in how 
 * they handle the optional attributes, so use them sparingly to improve the interoperability of your servlets.
 *
 * The servlet sends cookies to the browser by using the HttpServletResponse#addCookie method, which adds fields to HTTP response headers to 
 * send cookies to the browser, one at a time. The browser is expected to support 20 cookies for each Web server, 300 cookies total, and may 
 * limit cookie size to 4 KB each.
 *
 * The browser returns cookies to the servlet by adding fields to HTTP request headers. Cookies can be retrieved from a request by using the 
 * HttpRequest#getCookies method. Several cookies might have the same name but different path attributes.
 * Cookies affect the caching of the Web pages that use them. HTTP 1.0 does not cache pages that use cookies created with this class. This class 
 * does not support the cache control defined with HTTP 1.1.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
public final class Cookie {
  private static final SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss z");
  static {
    fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private final String name;
  private final String value;

  private String  path     = null;
  private String  comment  = null;
  private String  domain   = null;
  private boolean secure   = false;
  private boolean httpOnly = false;
  private int     maxAge   = -1;
  private int     version  = 0;

  /**
   * Constructor.
   * 
   * @param name Name of the cookie.
   * @param value Value of the cookie.
   * 
   */
  public Cookie(final String name, final String value) {
    this.name  = name;
    this.value = value;
  }

  /**
   * Returns the name of the cookie.
   * 
   * @return Name of the cookie.
   * 
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the value of the cookie.
   * 
   * @return Value of the cookie.
   * 
   */
  public String getValue() {
    return value;
  }
  
  /**
   * Specifies a comment that describes a cookie's purpose.
   * 
   * @param purpose Comment describing the purpose of this cookie.
   * 
   */
  public void setComment(final String purpose) {
    this.comment = purpose;
  }
  
  /**
   * Returns the comment describing the purpose of this cookie, or null if the cookie has no comment.
   * 
   * @return Comment describing the purpose of this cookie.
   * 
   */
  public String getComment() {
    return comment;
  }
  
  /**
   * Specifies the domain within which this cookie should be presented.
   * 
   * @param domain Domain within which this cookie should be presented.
   * 
   */
  public void setDomain(final String domain) {
    this.domain = domain;
  }
  
  /**
   * Returns the domain within which this cookie should be presented.
   * 
   * return Domain within which this cookie should be presented.
   * 
   */
  public String getDomain() {
    return domain;
  }
  
  /**
   * Sets the maximum age of the cookie in seconds.
   * 
   * @param expiry Maximum age of the cookie in seconds.
   * 
   */
  public void setMaxAge(int expiry) {
    maxAge = expiry;
  }
  
  /**
   * Returns the maximum age of the cookie, specified in seconds, By default, -1 indicating the cookie will persist until browser shutdown.
   *   
   * @return Maximum age of the cookie in seconds.
   * 
   */
  public int getMaxAge() {
    return maxAge;
  }
  
  /**
   * Specifies a path for the cookie to which the client should return the cookie.
   * 
   * @param uri Path on the server to which the browser returns this cookie.
   * 
   */
  public void setPath(final String uri) {
    path = uri;
  }

  /**
   * Returns the path on the server to which the browser returns this cookie.
   * 
   * @return Path on the server to which the browser returns this cookie.
   * 
   */
  public String getPath() {
    return path;
  }
  
  /**
   * Indicates to the browser whether the cookie should only be sent using a secure protocol, such as HTTPS or SSL.
   * 
   * @param flag true if the browser is sending cookies only over a secure protocol, or false if the browser can send cookies using any protocol.
   */
  public void setSecure(boolean flag) {
    secure = flag;
  }
  
  /**
   * Returns true if the browser is sending cookies only over a secure protocol, or false if the browser can send cookies using any protocol.
   * 
   * @return True if the browser is sending cookies only over a secure protocol, or false if the browser can send cookies using any protocol.
   * 
   */
  public boolean getSecure() {
    return secure;
  }

  /**
   * Sets the version of the cookie protocol this cookie complies with.
   * 
   * @param v Version of the protocol this cookie complies with.
   */
  public void setVersion(int v) {
    version = v;
  }

  /**
   * Returns the version of the protocol this cookie complies with.
   * 
   * @return Version of the protocol this cookie complies with.
   */
  public int getVersion() {
    return version;
  }

  /**
   * Marks or unmarks this Cookie as HttpOnly.
   *  
   * @param isHttpOnly true if this cookie is to be marked as HttpOnly, false otherwise.
   */
  public void setHttpOnly(boolean isHttpOnly) {
    httpOnly = isHttpOnly;
  }

  /**
   * Checks whether this Cookie has been marked as HttpOnly.
   *  
   * @return true if the Cookie is HttpOnly, otherwise false.
   * 
   */
  public boolean isHttpOnly() {
    return httpOnly;
  }
  
  /**
   * Returns a string representation of the cookie object.
   * 
   * return String representation of the cookie object.
   * 
   */
  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer();

    sb.append(name).append('=').append(value);
    
    if (domain != null)
      sb.append("; Domain=").append(domain);
    
    if (path != null)
      sb.append("; Path=").append(path);

    if (secure)
      sb.append("; Secure");
    
    if (httpOnly)
      sb.append("; HttpOnly");
    
    if (version != 0)
      sb.append("; Version=").append(version);
    
    if (comment != null)
      sb.append("; Comment=").append(comment);
    
    if (maxAge > -1) {
      long expires = System.currentTimeMillis() + (maxAge * 1000);
      sb.append("; Max-Age=").append(maxAge);
      
      Timestamp timestamp = new Timestamp(expires);
      sb.append("; Expires=").append(fmt.format(timestamp));
    }
    
    return sb.toString();
  }
}
