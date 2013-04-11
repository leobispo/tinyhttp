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

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;

/**
 * This class represents a part or form item that was received within a multipart/form-data POST request.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
public interface Part {

  /**
   * Gets the content type of this part.
   * 
   * @return The content type of this part.
   * 
   */
  public String getContentType();
  
  /**
   * Gets the name of this part.
   * 
   * @return The name of this part as a String.
   * 
   */
  public String getName();
  
  /**
   * Returns the name of the file stored in the multipart header.
   * 
   * @return The name of the file stored in the multipart header.
   */
  public String getFileName();
  
  /**
   * Returns the size of this file.
   * 
   * @return A long specifying the size of this part, in bytes.
   * 
   */
  public long getSize();
  
  /**
   * Deletes the underlying storage for a file item, including deleting any associated temporary disk file.
   * 
   */
  public void delete();
  
  /**
   * Returns the value of the specified mime header as a String. If the Part did not include a header of the specified name, 
   * this method returns null. If there are multiple headers with the same name, this method returns the first header in the part. 
   * The header name is case insensitive. You can use this method with any request header.

   * @param name A String specifying the header name.
   * 
   * @return A String containing the value of the requested header, or null if the part does not have a header of that name.
   * 
   */
  public String getHeader(final String name);
  
  /**
   * Gets the values of the Part header with the given name.
   * Any changes to the returned Collection must not affect this Part.
   * Part header names are case insensitive.
   * 
   * @return A (possibly empty) Enumeration of the header names of this Part.
   * 
   */
  public Enumeration<String> getHeaderNames();
  
  /**
   * Gets the content of this part as an InputStream.
   * 
   * @return The content of this part as an InputStream.
   * 
   */
  public InputStream getInputStream() throws IOException;
  
  /**
   * A convenience method to write this uploaded item to disk.
   * 
   * This method is not guaranteed to succeed if called more than once for the same part. This allows a particular implementation to use,
   * for example, file renaming, where possible, rather than copying all of the underlying data, thus gaining a significant performance benefit.
   * 
   * @param fileName The name of the file to which the stream will be written.
   * 
   * @throws IOException
   */
  public void write(final String fileName) throws IOException;
}
