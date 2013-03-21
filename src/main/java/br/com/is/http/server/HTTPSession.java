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

import java.util.Enumeration;
import java.util.Hashtable;

public class HTTPSession {
  private final Hashtable<String, Cookie> cookies    = new Hashtable<>();
  private final Hashtable<String, Object> attributes = new Hashtable<>();
  
  HTTPSession(final String id) {
    cookies.put("ISSESSIONID", new Cookie("ISSESSIONID", id)); //TODO: Set the expiration date
  }
  
  public Object getAttribute(final String name) {
    return attributes.get(name);
  }
  
  public Enumeration<String> getAttributeNames() {
    return attributes.keys();
  }
  
  public long getCreationTime() {
    return 0;
  }
  
  public String getId() {
    return cookies.get("ISSESSIONID").getValue();
  }
  
  public long getLastAccessedTime() {
    return 0;
  }
  
  public int getMaxInactiveInterval() {
    return 0;
  }
  
  public void invalidate() {
    
  }
  
  public boolean isNew() {
    return false;
  }
  
  public void removeAttribute(final String name) {
    attributes.remove(name);
  }
  
  public void setAttribute(final String name, final Object value) {
    attributes.put(name, value);
  }
  
  public void setMaxInactiveInterval(int interval) {
    
  }
}
