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
import java.util.concurrent.ConcurrentHashMap;

import br.com.is.nio.listener.TimerListener;

/**
 * This class will store information related to an HTTP Session. Since HTTP is a stateless protocol, we
 * are storing a Cookie ISSESSIONID in the client and reversing the id to keep the session alive. To not
 * keep the session forever, it will have an expiration time.
 * 
 * @author Leonardo Bispo de Oliveira
 *
 */
public class HTTPSession implements TimerListener {
  private final Hashtable<String, Object>              attributes = new Hashtable<>();
  private final ConcurrentHashMap<String, HTTPSession> sessions;
  
  private long         lastAccessTime = System.currentTimeMillis();
  private final long   creationTime   = System.currentTimeMillis();
  private final String id;
  
  /**
   * Constructor.
   * 
   * @param id HTTP Session unique id.
   * 
   */
  HTTPSession(final String id, final ConcurrentHashMap<String, HTTPSession> sessions) {
    this.id       = id;
    this.sessions = sessions;
  }
  
  /**
   * Returns the HTTP Session unique id.
   * 
   * @return HTTP Session unique id.
   * 
   */
  public String getId() {
    return id;
  }

  /**
   * Add a new attribute to this session.
   * 
   * @param name Attribute name.
   * @param value Attribute value.
   * 
   */
  public void setAttribute(final String name, final Object value) {
    attributes.put(name, value);
  }

  /**
   * Returns the attribute linked to the name passed as parameter.
   * 
   * @param name Attribute name.
   * 
   * @return Attribute value.
   * 
   */
  public Object getAttribute(final String name) {
    return attributes.get(name);
  }
  
  /**
   * Returns the attribute names stored in this session.
   *  
   * @return Attribute names stored in this session.
   */
  public Enumeration<String> getAttributeNames() {
    return attributes.keys();
  }
  
  /**
   * Remove the attribute linked to the name passed as parameter.
   * 
   * @param name Attribute name to be removed.
   * 
   */
  public void removeAttribute(final String name) {
    attributes.remove(name);
  }
  
  /**
   * Return the session creation time.
   * 
   * @return Session creation time.
   * 
   */
  public long getCreationTime() {
    return creationTime;
  }

  /**
   * Returns the last session access time.
   * 
   * @return Last session access time.
   * 
   */
  public long getLastAccessTime() {
    return lastAccessTime;
  }

  /**
   * Invalidate this session. It will remove the session ID from the sessions list.
   * 
   */
  public void invalidate() {
    sessions.remove(this);
  }
  
  /**
   * Internal method used to set the last access time.
   * 
   * @param lastAccessTime Last access time.
   * 
   */
  void setLastAccessTime(final long lastAccessTime) {
    this.lastAccessTime = lastAccessTime;
  }

  @Override
  public void timeout() {
    invalidate();
  }
}
