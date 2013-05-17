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

public abstract class HTTPContext {
  private boolean codeEncoding = false;
  private long length          = Long.MAX_VALUE;
  private String tempDirectory = System.getProperty("java.io.tmpdir");
  
  public void doDelete(final HTTPRequest req, final HTTPResponse resp) {
    resp.setStatus(HTTPStatus.METHOD_NOT_ALLOWED);
  }
  
  public void doGet(final HTTPRequest req, final HTTPResponse resp) { 
    resp.setStatus(HTTPStatus.METHOD_NOT_ALLOWED);
  }

  public void doPost(final HTTPRequest req, final HTTPResponse resp) {
    resp.setStatus(HTTPStatus.METHOD_NOT_ALLOWED);
  }

  public void doPut(final HTTPRequest req, final HTTPResponse resp) {
    resp.setStatus(HTTPStatus.METHOD_NOT_ALLOWED);
  }
  
  public void doTrace(final HTTPRequest req, final HTTPResponse resp) {
    resp.setStatus(HTTPStatus.METHOD_NOT_ALLOWED);
  }
  
  public void setTempDirectory(final String directory) {
    this.tempDirectory = directory;
  }

  public String getTempDirectory() {
    return tempDirectory;
  }

  public void setMaxContentLength(final long length) {
    this.length = length;
  }
  
  public long getMaxContentLenght() {
    return length;
  }
  
  public void setUseCodeEncoding(boolean codeEncoding) {
    this.codeEncoding = codeEncoding;
  }
  
  public boolean useCodeEncoding() {
    return codeEncoding;
  }
}
