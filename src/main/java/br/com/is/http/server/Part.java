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

import java.io.InputStream;
import java.util.Collection;

public class Part {

  public void delete() {
    
  }
  
  public String getContentType() {
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
  
  public InputStream getInputStream() {
    return null;
  }
  
  public String getName() {
    return "";
  }
  
  public long getSize() {
    return 0;
  }
  
  public void write(final String fileName) {
  }
}
