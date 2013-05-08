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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import br.com.is.http.server.HTTPContext;
import br.com.is.http.server.HTTPRequest;
import br.com.is.http.server.HTTPResponse;
import br.com.is.http.server.Part;
import br.com.is.http.server.exception.BadRequestException;
import br.com.is.http.server.exception.HTTPRequestException;
import br.com.is.http.server.exception.InternalServerErrorException;
import br.com.is.http.server.exception.RequestEntityTooLargeException;

public class MultipartFormData implements HTTPMediaType {
  private enum Type { BEGIN, FORM, FILE };
  private enum BoundaryCheck { NOT_BOUNDARY, NOT_SURE, BOUNDARY };
  private static int BUFFER_SIZE                            = 8192; // Must be power of two!

  private static final int    MASK                          = BUFFER_SIZE - 1;
  private static final String CONTENT_TYPE                  = "content-type";
  private static final String CONTENT_DISPOSITION           = "content-disposition";
  private static final String CONTENT_DISPOSITION_NAME      = "name";
  private static final String CONTENT_DISPOSITION_FILE_NAME = "filename";
  
  private static final String BOUNDARY                      = "boundary";
  
  private int readPtr          = 0;
  private int available        = BUFFER_SIZE;
  private final byte cbuffer[] = new byte[BUFFER_SIZE];
  
  @Override
  public void process(final HTTPContext context, final HTTPRequest request, final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams,
    final Hashtable<String, Part> parts) throws HTTPRequestException {
    
    final String boundaryAttribute[] = parameter.split("=");
    
    if (boundaryAttribute.length != 2 || !boundaryAttribute[0].trim().equalsIgnoreCase(BOUNDARY))
      throw new BadRequestException("Invalid media type parameter: " + parameter);
    
    final String boundary       = "--" + boundaryAttribute[1].trim();
    final String boundaryMiddle = "\r\n" + boundary;
    final InputStream is  = request.getInputStream();
    
    Type type   = Type.BEGIN;
    boolean end = false;
    
    String name = null;
    PartImpl currPart = null;
    boolean dumpEnded = false;
    int currPtr = 0;
    try {
      while (!end || (available < BUFFER_SIZE)) {
        if (end && (available < BUFFER_SIZE && currPtr == readPtr))
          throw new BadRequestException("Malformed Request Body");

        currPtr = readPtr;
        if (available == 0)
          throw new RequestEntityTooLargeException("Cannot keep more than " + BUFFER_SIZE + " in buffer");
        try {
          int count = 0;
          while (!end && available > 0 && count++ < 2) {
            int writePtr = ((BUFFER_SIZE - available) + readPtr) & MASK;
            int read     = is.read(cbuffer, writePtr, (BUFFER_SIZE - writePtr) >= available ? available : (BUFFER_SIZE - writePtr));
            if (read != -1)
              available -= read;
            else
              end = true;
          }
        }
        catch (IOException e) {
          throw new InternalServerErrorException("Problems to read data from the HTTP Channel", e);
        }

        if (type == Type.BEGIN) {
          final Hashtable<String, String> header = parseBoundary(boundary);
          if (header == null && end)
            throw new BadRequestException("Problems to parse the Multipart-Form");
          else if (header != null) {
            if (!header.isEmpty()) {
              String cDisposition = header.get(CONTENT_DISPOSITION);
              if (cDisposition == null)
                throw new BadRequestException("Wrong form-data. No valid content disposition");

              final Hashtable<String, String> disposition = parseDisposition(cDisposition);

              name = disposition.get(CONTENT_DISPOSITION_NAME);
              if (name == null)
                throw new BadRequestException("Wrong form-data. No valid content disposition");

              name = name.replaceAll("\"", "");
              if (header.containsKey(CONTENT_TYPE)) {
                String fileName = disposition.get(CONTENT_DISPOSITION_FILE_NAME);
                if (fileName != null) {
                  fileName = fileName.replaceAll("\"", "");
                  if (!fileName.isEmpty()) {
                    try {
                      type = Type.FILE;
                      currPart = new PartImpl(name, fileName, header, context.getTempDirectory());
                    }
                    catch (IOException ie) {
                      throw new InternalServerErrorException("Problem to create the temporary file", ie);
                    }
                  }
                }
              }
              else
                type = Type.FORM;
            }
            else //This means that I found the end of the boundary. just stop the processing
              end = true;
          }
        }
        else if (type == Type.FORM)
          parseForm(name, requestParams);
        else 
          dumpEnded = dumpTempData(currPart, boundaryMiddle);

        if (dumpEnded || (type != Type.FILE && isBoundary(readPtr, boundaryMiddle) == BoundaryCheck.BOUNDARY)) {
          dumpEnded = false;
          discardBuffer(2);
          type = Type.BEGIN;
          if (currPart != null) {
            try {
              currPart.os.close();
              parts.put(currPart.name, currPart);
              currPart = null;
            }
            catch (IOException e) {
              throw new InternalServerErrorException("Problem to close the temporary file", e);
            }
          }
          name = null;
        }
      }

      context.doPost(request, response);
    }
    finally {
      Iterator<Map.Entry<String, Part>> it = parts.entrySet().iterator();

      while (it.hasNext()) {
        Map.Entry<String, Part> entry = it.next();

        if (entry.getKey() != null)
          ((PartImpl) entry.getValue()).deleteTempFile();
        
        if (currPart != null)
          currPart.deleteTempFile();
      }
    }
  }
  
  private void parseForm(final String name, final Hashtable<String, String> requestParams) {
    StringBuilder builder = new StringBuilder();
    
    int bytesRead = 0;
    int currPtr   = readPtr;
    int availableRead = (BUFFER_SIZE - available);
    while (availableRead-- >= 2) {
      if (cbuffer[currPtr & MASK] == '\r' && cbuffer[(currPtr + 1) & MASK] == '\n') {
        requestParams.put(name, builder.toString());
        discardBuffer(bytesRead);
        return;
      }
      else {
        ++bytesRead;
        builder.append((char) cbuffer[currPtr++ & MASK]);
      }
    }
  }
  
  private Hashtable<String, String> parseBoundary(final String boundary) {
    if (isBoundary(readPtr, boundary) != BoundaryCheck.BOUNDARY || (BUFFER_SIZE - (available + boundary.length()) < 2))
      return null; 
    
    int bytesRead = 0;
    final Hashtable<String, String> header = new Hashtable<>();
    int currPtr = (readPtr + boundary.length());
    if (cbuffer[currPtr & MASK] == '-' && cbuffer[(currPtr + 1) & MASK] == '-') { // End of the boundaries
      discardBuffer(boundary.length() + 4);
      return header;
    }

    currPtr   += 2;
    bytesRead += 2 + boundary.length();
    int availableRead = (BUFFER_SIZE - (available + boundary.length()));
    boolean isNewLine = false;
    
    StringBuilder builder = new StringBuilder();
    String headerField = "";
    while (availableRead-- >= 2) {
      if (cbuffer[currPtr & MASK] == '\r' && cbuffer[(currPtr + 1) & MASK] == '\n') {
        if (isNewLine) {
          if (!headerField.isEmpty()) {
            int idx = headerField.indexOf(':');
            if (idx != -1)
              header.put(headerField.substring(0, idx).trim().toLowerCase(), headerField.substring(idx + 1).trim());
          }
            
          discardBuffer(bytesRead + 2);
          return header;
        }

        String line = builder.toString();
        if (!line.isEmpty()) {         
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

          builder = new StringBuilder();
        }
        currPtr   += 2;
        bytesRead += 2;
        isNewLine = true;
      }
      else {
        ++bytesRead;
        builder.append((char) cbuffer[currPtr++ & MASK]);
        isNewLine = false;
      }
    }

    return null;
  }
  
  private BoundaryCheck isBoundary(int ptr, final String boundary) {
    byte b[] = boundary.getBytes();
    if (b.length > (BUFFER_SIZE - available))
      return BoundaryCheck.NOT_SURE;
    
    for (int i = 0; i < b.length; ++i) {
      if (b[i] != cbuffer[(ptr + i) & MASK])
        return BoundaryCheck.NOT_BOUNDARY;
    }
    
    return BoundaryCheck.BOUNDARY;
  }

  private boolean dumpTempData(final PartImpl part, final String boundary) throws HTTPRequestException {
    if (part == null)
      throw new InternalServerErrorException("Problems to create a temporary file to store the incomming files");

    int bytesRead = 0;
    int currPtr   = readPtr;
    int availableRead = (BUFFER_SIZE - available);
    while (availableRead-- >= 2) {
      if (cbuffer[currPtr & MASK] == '\r' && cbuffer[(currPtr + 1) & MASK] == '\n') {
        BoundaryCheck check = isBoundary(currPtr, boundary);
        if (check == BoundaryCheck.BOUNDARY) {
          flushBuffer(bytesRead, part);
          return true;
        }

        if (check == BoundaryCheck.NOT_SURE)
          break;
        currPtr   += 2;
        bytesRead += 2;
      }
      else {
        ++bytesRead;
        ++currPtr;
      }
    }
    
    flushBuffer(bytesRead, part);
    return false;
  }
  
  private void flushBuffer(int bytesRead, final PartImpl part) throws HTTPRequestException {
    while (bytesRead > 0) {
      int write = ((BUFFER_SIZE - (readPtr & MASK)) < bytesRead) ? (BUFFER_SIZE - (readPtr & MASK)) : bytesRead;
      try {
        part.os.write(cbuffer, readPtr & MASK, write);
      }
      catch (IOException e) {
        throw new InternalServerErrorException("Problems to write to the temporary file");
      }
      bytesRead -= write;
      discardBuffer(write);
    }
  }
  
  private void discardBuffer(int size) {
    readPtr    = (readPtr + size);
    available += size;
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
    private final FileOutputStream          os;
    
    private boolean moved = false;
    
    /**
     * Constructor.
     * 
     * @param name Name of this part.
     * @param fileName Name of the file stored in the multipart header.
     * @param tempFile Name of the temporary file.
     * @param header The part header.
     * 
     */
    public PartImpl(final String name, final String fileName, final Hashtable<String, String> header, final String tempDirectory) throws IOException {
      this.name     = name;
      this.fileName = fileName;
      this.tempFile = File.createTempFile(fileName, ".tmp", new File(tempDirectory)); 
      this.os       = new FileOutputStream(tempFile);
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
      moved = true;
      tempFile.renameTo(new File(fileName));
    }
    
    private void deleteTempFile() {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {}
      }
      
      if (!moved)
        tempFile.delete();
    }
  }
}
