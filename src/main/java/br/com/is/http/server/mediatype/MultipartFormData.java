package br.com.is.http.server.mediatype;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
  private static final String CONTENT_DISPOSITION           = "content-disposition";
  private static final String CONTENT_DISPOSITION_NAME      = "name";
  private static final String CONTENT_DISPOSITION_FILE_NAME = "file-name";
  private static final String CONTENT_TYPE                  = "content-type";
  
  @Override
  public void process(final HTTPContext context, final HTTPRequest request,final HTTPResponse response,
    final String parameter, final Hashtable<String, String> requestParams,
    final Hashtable<String, Part> parts) throws HTTPRequestException {
    final String boundaryAttribute[] = parameter.split("=");
    
    if (boundaryAttribute.length != 2 || !boundaryAttribute[0].trim().equalsIgnoreCase("boundary"))
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
              String fileName = header.get(CONTENT_DISPOSITION_FILE_NAME);
              if (fileName != null && !fileName.isEmpty()) {
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
        else if (os != null) {
          os.close();
        }
      } while (line != null && !line.equals(boundaryEnd));
      
      context.doPost(request, response);
    }
    catch (IOException e) {
      throw new InternalServerErrorException("Problems to read data from the HTTP Channel", e);
    }
    finally {
      //TODO: For each part, ask to delete the temp file
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
  
  private class PartImpl implements Part, Closeable {
    private final Hashtable<String, String> header;
    private final File   tempFile;
    private final String name;
    private final String fileName;
    
    private FileInputStream is = null;
    
    public PartImpl(final String name, final String fileName, final File tempFile, final Hashtable<String, String> header) {
      this.name     = name;
      this.fileName = fileName;
      this.tempFile = tempFile;
      this.header   = header;
      
      tempFile.deleteOnExit();
    }
    
    @Override
    public void delete() {
      tempFile.delete();
    }

    @Override
    public String getContentType() {
      return header.get(CONTENT_TYPE);
    }

    @Override
    public String getHeader(String name) {
      return header.get(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return header.keys();
    }

    @Override
    public InputStream getInputStream() {
      if (is == null) {
        try {
          is = new FileInputStream(tempFile);
        }
        catch (FileNotFoundException e) {
          return null;
        }
      }
      
      return is;
    }

    @Override
    public String getName() {
      return name;
    }
    
    @Override
    public String getFileName() {
      return fileName;
    }

    @Override
    public long getSize() {
      return tempFile.length();
    }

    @Override
    public void write(String fileName) {
      tempFile.renameTo(new File(fileName));
    }

    @Override
    public void close() throws IOException {
      if (tempFile != null)
        tempFile.delete();
    }
  }
}
