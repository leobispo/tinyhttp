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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class HTTPAnnotatedContext extends HTTPContext {
  protected final Object clazz;
  protected final Method deleteMethod;
  protected final Method getMethod;
  protected final Method postMethod;
  protected final Method putMethod;
  protected final Method traceMethod;
  
  protected HTTPAnnotatedContext(final Object clazz, final Method getMethod, Method postMethod, Method deleteMethod, Method traceMethod,
    Method putMethod) {
    this.clazz        = clazz;
    this.getMethod    = getMethod;
    this.postMethod   = postMethod;
    this.deleteMethod = deleteMethod;
    this.traceMethod  = traceMethod;
    this.putMethod    = putMethod;
  }
  
  public void doDelete(final HTTPRequest req, final HTTPResponse resp) {
    if (deleteMethod != null) {
      try {
        deleteMethod.invoke(clazz, req, resp);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        resp.setStatus(HTTPStatus.INTERNAL_SERVER_ERROR);
      }
    }
    else
      super.doDelete(req, resp);
  }
  
  public void doGet(final HTTPRequest req, final HTTPResponse resp) { 
    if (getMethod != null) {
      try {
        getMethod.invoke(clazz, req, resp);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        resp.setStatus(HTTPStatus.INTERNAL_SERVER_ERROR);
      }
    }
    else
      super.doGet(req, resp);
  }

  public void doPost(final HTTPRequest req, final HTTPResponse resp) {
    if (postMethod != null) {
      try {
        postMethod.invoke(clazz, req, resp);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        resp.setStatus(HTTPStatus.INTERNAL_SERVER_ERROR);
      }
    }
    else
      super.doPost(req, resp);
  }

  public void doPut(final HTTPRequest req, final HTTPResponse resp) {
    if (putMethod != null) {
      try {
        putMethod.invoke(clazz, req, resp);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        resp.setStatus(HTTPStatus.INTERNAL_SERVER_ERROR);
      }
    }
    else
      super.doPut(req, resp);
  }
  
  public void doTrace(final HTTPRequest req, final HTTPResponse resp) {
    if (traceMethod != null) {
      try {
        traceMethod.invoke(clazz, req, resp);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        resp.setStatus(HTTPStatus.INTERNAL_SERVER_ERROR);
      }
    }
    else
      super.doTrace(req, resp);
  }
}