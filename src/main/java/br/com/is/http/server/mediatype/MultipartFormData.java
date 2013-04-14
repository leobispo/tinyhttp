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
package br.com.is.http.server.mediatype;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;
import br.com.is.http.server.Part;
import br.com.is.http.server.exception.BadRequestException;
import br.com.is.http.server.exception.HTTPRequestException;
import br.com.is.http.server.exception.InternalServerErrorException;

public class MultipartFormData implements HTTPMediaType {
  private static final String CONTENT_TYPE                  = "content-type";
  private static final String CONTENT_DISPOSITION           = "content-disposition";
  private static final String CONTENT_DISPOSITION_NAME      = "name";
  private static final String CONTENT_DISPOSITION_FILE_NAME = "filename";
  
  private static final String BOUNDARY                      = "boundary";
  
  @Override
  public void process(final HTTPContext context, final HTTPRequest request,final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams,
    final Hashtable<String, Part> parts) throws HTTPRequestException {
    
    //TODO: This code Is wrong. I cannot handle the content media as string! Instead of, read an amount of buffer, check for the special bondary character and got to next
    final String boundaryAttribute[] = parameter.split("=");
    
    if (boundaryAttribute.length != 2 || !boundaryAttribute[0].trim().equalsIgnoreCase(BOUNDARY))
      throw new BadRequestException("Invalid media type parameter: " + parameter);
    
    final String boundary    = "--" + boundaryAttribute[1].trim();
    final String boundaryEnd = boundary + "--";
    
    final BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()));

    String line;
    try {
      do {
        final Hashtable<String, String> header = new Hashtable<>();
        boolean body        = false;
        Part    part        = null;
        String  paramsName  = null;
        String  paramsValue = null;
        String  headerField = "";
        
        FileOutputStream os = null;
        while ((line = reader.readLine()) != null && !line.startsWith(boundary)) {
          if (line.isEmpty()) {
            if (part != null) {
              parts.put(part.getName(), part);
              part = null;
              
              if (os != null)
                os.close();
            }
            if (!headerField.isEmpty()) {
              int idx = headerField.indexOf(':');
              if (idx != -1)
                header.put(headerField.substring(0, idx).trim().toLowerCase(), headerField.substring(idx + 1).trim());
            }
            
            String cDisposition = header.get(CONTENT_DISPOSITION);
            if (cDisposition == null)
              throw new BadRequestException("Wrong form-data. No valid content disposition");
            
            final Hashtable<String, String> disposition = parseDisposition(cDisposition);
            
            final String name = disposition.get(CONTENT_DISPOSITION_NAME);
            if (name == null)
              throw new BadRequestException("Wrong form-data. No valid content disposition");
            
            if (header.containsKey(CONTENT_TYPE)) {
              String fileName = disposition.get(CONTENT_DISPOSITION_FILE_NAME);
              if (fileName != null && !fileName.isEmpty()) {
                fileName = fileName.replaceAll("\"", "");
                final File tempFile = File.createTempFile(fileName, ".tmp", new File(context.getTempDirectory()));
                os   = new FileOutputStream(tempFile);
                part = new PartImpl(name, fileName, tempFile, header);
              }
            }
            else
              paramsName = name;

            body = true;
          }
          else if (!body) {
            if (line.indexOf(' ') != 0 && line.indexOf('\t') != 0) {
              if (!headerField.isEmpty()) {
                int idx = headerField.indexOf(':');
                if (idx != -1)
                  header.put(headerField.substring(0, idx).trim().toLowerCase(), headerField.substring(idx + 1).trim());
              }
              headerField = line;
            }
            else
              headerField += line;
          }
          else {
            if (part == null)
              paramsValue = paramsValue == null ? line : paramsValue + line;
            else if (os != null)
              os.write(line.getBytes());
          }
        }
        
        if (part == null) {
          if (paramsName != null && paramsValue != null)
            requestParams.put(paramsName, paramsValue);
        }
        else if (part != null) {
          parts.put(part.getName(), part);
          part = null;
          
          if (os != null)
            os.close();
        }

      } while (line != null && !line.equals(boundaryEnd));
      
      context.doPost(request, response);
    }
    catch (IOException e) {
      throw new InternalServerErrorException("Problems to read data from the HTTP Channel", e);
    }
  }
  
  private Hashtable<String, String> parseDisposition(String disposition) {
    final Hashtable<String, String> ret = new Hashtable<>();
    final StringTokenizer st = new StringTokenizer(disposition, ";");
    while (st.hasMoreTokens()) {
      String token = st.nextToken().trim();
      int idx = token.indexOf('=');
      if (idx != -1)
        ret.put(token.substring(0, idx).trim().toLowerCase(), token.substring(idx + 1).trim());
    }
    
    return ret;
  }
  
  /**
   * This class represents a part or form item that was received within a multipart/form-data POST request.
   * 
   * @author Leonardo Bispo de Oliveira.
   *
   */
  private class PartImpl implements Part {
    private final Hashtable<String, String> header;
    private final File                      tempFile;
    private final String                    name;
    private final String                    fileName;
    
    private FileInputStream                 is = null;
    
    /**
     * Constructor.
     * 
     * @param name Name of this part.
     * @param fileName Name of the file stored in the multipart header.
     * @param tempFile Name of the temporary file.
     * @param header The part header.
     * 
     */
    public PartImpl(final String name, final String fileName, final File tempFile, final Hashtable<String, String> header) {
      this.name     = name;
      this.fileName = fileName;
      this.tempFile = tempFile;
      this.header   = header;
      
      tempFile.deleteOnExit();
    }
    
    /**
     * Gets the content type of this part.
     * 
     * @return The content type of this part.
     * 
     */
    @Override
    public String getContentType() {
      return header.get(CONTENT_TYPE);
    }
    
    /**
     * Gets the name of this part.
     * 
     * @return The name of this part as a String.
     * 
     */
    @Override
    public String getName() {
      return name;
    }
    
    /**
     * Returns the name of the file stored in the multipart header.
     * 
     * @return The name of the file stored in the multipart header.
     */
    @Override
    public String getFileName() {
      return fileName;
    }
    
    /**
     * Returns the size of this file.
     * 
     * @return A long specifying the size of this part, in bytes.
     * 
     */
    @Override
    public long getSize() {
      return tempFile.length();
    }
    
    /**
     * Deletes the underlying storage for a file item, including deleting any associated temporary disk file.
     * 
     */
    @Override
    public void delete() {
      tempFile.delete();
    }
    
    /**
     * Returns the value of the specified mime header as a String. If the Part did not include a header of the specified name, 
     * this method returns null. If there are multiple headers with the same name, this method returns the first header in the part. 
     * The header name is case insensitive. You can use this method with any request header.

     * @param name A String specifying the header name.
     * 
     * @return A String containing the value of the requested header, or null if the part does not have a header of that name.
     * 
     */
    @Override
    public String getHeader(String name) {
      return header.get(name);
    }
    
    /**
     * Gets the values of the Part header with the given name.
     * Any changes to the returned Collection must not affect this Part.
     * Part header names are case insensitive.
     * 
     * @return A (possibly empty) Enumeration of the header names of this Part.
     * 
     */
    @Override
    public Enumeration<String> getHeaderNames() {
      return header.keys();
    }
    
    /**
     * Gets the content of this part as an InputStream.
     * 
     * @return The content of this part as an InputStream.
     * 
     */
    @Override
    public InputStream getInputStream() throws IOException {
      if (is == null)
        is = new FileInputStream(tempFile);
      
      return is;
    }
    
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
    @Override
    public void write(String fileName) throws IOException {
      tempFile.renameTo(new File(fileName));
    }
  }
}
