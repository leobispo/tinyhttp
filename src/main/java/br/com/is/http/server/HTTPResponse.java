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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;

public interface HTTPResponse {
  public void addCookie(Cookie cookie);

  public void addHeader(final String name, final String value);
  
  public boolean containsHeader(final String name);
  
  public String getHeader(final String name);
  
  public Enumeration<String> getHeaderNames();
  
  public int getStatus();

  public void sendRedirect(final String location) throws IllegalStateException;
  
  public void setStatus(HTTPStatus sc);
  
  public OutputStream getOutputStream();
  
  public PrintWriter getWriter();
}
