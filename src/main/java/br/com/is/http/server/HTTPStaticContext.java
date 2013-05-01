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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import br.com.is.http.server.exception.BadRequestException;
import br.com.is.http.server.exception.HTTPRequestException;
import br.com.is.http.server.exception.InternalServerErrorException;
import br.com.is.http.server.exception.RequestRangeNotSatisfiableException;

//TODO: Implement the Authentication Method.!!
public final class HTTPStaticContext extends HTTPContext {
  private static final String MIME_DEFAULT_BINARY = "application/octet-stream";
  
  private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  
  private final String path;
  private final List<String> defaultPages;

  private static Hashtable<String, String> MimeTypes = new Hashtable<>();
  static {
    MimeTypes.put("css"  , "text/css"                     );
    MimeTypes.put("htm"  , "text/html"                    );
    MimeTypes.put("html" , "text/html"                    );
    MimeTypes.put("xml"  , "text/xml"                     );
    MimeTypes.put("txt"  , "text/plain"                   );
    MimeTypes.put("asc"  , "text/plain"                   );
    MimeTypes.put("gif"  , "image/gif"                    );
    MimeTypes.put("jpg"  , "image/jpeg"                   );
    MimeTypes.put("jpeg" , "image/jpeg"                   );
    MimeTypes.put("png"  , "image/png"                    );
    MimeTypes.put("mp3"  , "audio/mpeg"                   );
    MimeTypes.put("m3u"  , "audio/mpeg-url"               );
    MimeTypes.put("mp4"  , "video/mp4"                    );
    MimeTypes.put("ogv"  , "video/ogg"                    );
    MimeTypes.put("flv"  , "video/x-flv"                  );
    MimeTypes.put("mov"  , "video/quicktime"              );
    MimeTypes.put("swf"  , "application/x-shockwave-flash");
    MimeTypes.put("js"   , "application/javascript"       );
    MimeTypes.put("pdf"  , "application/pdf"              );
    MimeTypes.put("doc"  , "application/msword"           );
    MimeTypes.put("ogg"  , "application/x-ogg"            );
    MimeTypes.put("zip"  , MIME_DEFAULT_BINARY            );
    MimeTypes.put("exe"  , MIME_DEFAULT_BINARY            );
    MimeTypes.put("class", MIME_DEFAULT_BINARY            );
  }
  
  public HTTPStaticContext(final String path) {
    this.path         = path;
    this.defaultPages = new ArrayList<>();
  }
  
  public HTTPStaticContext(final String path, final List<String> defaultPages) {
    this.path         = path;
    this.defaultPages = defaultPages;
  }
  
  @Override
  public void doGet(final HTTPRequest req, final HTTPResponse resp) { 
    try {
      final String uri = normalizeURI(req.getRequestURI());

      final File staticUriInfo = new File(path, uri);
      File staticFile = null;
      
      resp.addHeader("Accept-Ranges", "bytes");
      if (staticUriInfo.exists()) {
        if (staticUriInfo.isDirectory()) {
          if (uri.endsWith("/")) {
            for (String page : defaultPages) {
              File tmp = new File(staticUriInfo, page);
              if (tmp.exists()) {
                staticFile = tmp;
                break;
              }
            }
            
            if (staticFile == null)
              processDirectory(req, resp, staticUriInfo, uri);
          }
          else
            resp.sendRedirect(uri + "/");
        }
        else
          staticFile = staticUriInfo;
        
        if (staticFile != null)
          processFile(req, resp, staticFile);
      }
      else
        resp.setStatus(HTTPStatus.NOT_FOUND);
    }
    catch (HTTPRequestException e) {
      if (LOGGER.isLoggable(Level.WARNING))
        LOGGER.log(Level.WARNING, "Problems Create an HTTP Response", e);

      resp.setStatus(e.getError());
    }
  }
  
  private void processDirectory(final HTTPRequest req, final HTTPResponse resp, final File dir, final String uri) throws HTTPRequestException {
    if (!dir.canRead())
      throw new BadRequestException("Directory not accessible");
    
    PrintWriter writer = resp.getWriter();
    writer.println("<html>");
    writer.println("  <body>"); 
    
    if (uri.length() > 1) {
      String u = uri.substring(0, uri.length() - 1);
      int slash = u.lastIndexOf('/');
      if (slash >= 0 && slash  < u.length()) {
        writer.print("    <b><a href=\"");
        writer.print(uri.substring(0, slash + 1));
        writer.println("\">..</a></b><br/>");
      }
    }
    
    for (String file : dir.list()) {
      File curr = new File(dir, file);
      if (curr.isDirectory()) {
        writer.print("    <b>");
        file += "/";
      }
      
      writer.print("<a href=\"");
      writer.print(encodeUri(uri + file));
      writer.print("\">");
      writer.print(file);
      writer.print("</a>");
      
      if (curr.isFile()) {
        final long len = curr.length();
        writer.print(" &nbsp;<font size=2>(");
        if (len < 1024) {
          writer.print(len);
          writer.print(" bytes");
        }
        else if (len < (1024 * 1024)) {
          writer.print(len / 1024);
          writer.print(".");
          writer.print(len % 1024 / 10 % 100);
          writer.print(" KB");
        }
        else {
          writer.print(len / (1024 * 1024));
          writer.print(".");
          writer.print(len % (1024 * 1024) / 10 % 100);
          writer.print(" MB");
        }

        writer.println(")</font><br>");
      }
      else
        writer.println("</b><br>");
    }
  
    writer.println("  </body>");
    writer.println("</html>");
    
  }
  
  private void processFile(final HTTPRequest req, final HTTPResponse resp, final File file) throws HTTPRequestException {
    if (!file.canRead())
      throw new BadRequestException("File not accessible");

    String mime = null;
    try {
      final int dot = file.getCanonicalPath().lastIndexOf('.');
      if (dot >= 0)
        mime = MimeTypes.get(file.getCanonicalPath().substring(dot + 1).toLowerCase());
    }
    catch (IOException e) {
      throw new InternalServerErrorException("Problems to get information from the file", e);
    }
    if (mime == null)
      mime = MIME_DEFAULT_BINARY;

    final String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + "" + file.length()).hashCode());

    long startFrom = 0;
    long endAt     = -1;
    String range = req.getHeader("range");
    if (range != null) {
      if (range.startsWith("bytes=")) {
        range = range.substring("bytes=".length());
        final int minus = range.indexOf('-');
        try {
          if ( minus > 0 ) {
            startFrom = Long.parseLong( range.substring(0, minus));
            endAt = Long.parseLong(range.substring(minus + 1));
          }
        }
        catch (NumberFormatException e) {
          throw new BadRequestException("Invalid number", e);
        }
      }
    }
    
    final long fileLen = file.length();
    if (range != null && startFrom >= 0) {
      if (startFrom >= fileLen) {
        resp.addHeader("Content-Range", "bytes 0-0/" + fileLen);
        resp.addHeader("ETag", etag);

        if (mime.startsWith( "application/"))
          resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

        throw new RequestRangeNotSatisfiableException("Not valid range passed in the HTTP header");
      }
      else {
        if (endAt < 0)
          endAt = fileLen - 1;

        long newLen = endAt - startFrom + 1;
        if (newLen < 0)
          newLen = 0;

        final long dataLen = newLen;
        try {
          final InputStream is = new FileInputStream(file) {
            public int available() throws IOException {
              return (int) dataLen;
            }
          };
          
          is.skip(startFrom);
          
          IOUtils.copy(is, resp.getOutputStream());
          resp.addHeader("Content-Length", Long.toString(dataLen));
          resp.addHeader( "Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
          if (mime.startsWith("application/"))
            resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

          resp.addHeader("ETag", etag);
          
          resp.setStatus(HTTPStatus.PARTIAL_CONTENT);
        }
        catch (IOException e) {
          throw new InternalServerErrorException("Problems to open the file", e);
        }
      }
    }
    else if (!etag.equals(req.getHeader("if-none-match"))) {
      try {
        IOUtils.copy(new FileInputStream(file), resp.getOutputStream());
      }
      catch (IOException e) {
        throw new InternalServerErrorException("Problems to open the file", e);
      }

      resp.addHeader("Content-Length", Long.toString(fileLen));

      if (mime.startsWith( "application/" ))
        resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

      resp.addHeader("ETag", etag);
    }
    else
      resp.setStatus(HTTPStatus.NOT_MODIFIED);
  }
  
  private String normalizeURI(String uri) throws HTTPRequestException {
    uri = uri.trim().replace(File.separatorChar, '/');
    if (uri.indexOf('?') >= 0 )
      uri = uri.substring(0, uri.indexOf('?'));

    if (uri.startsWith("..") || uri.endsWith("..") || uri.indexOf("../") >= 0)
      throw new BadRequestException("Cannot call .. to navigate between folders");
    
    return uri;
  }
  
  private String encodeUri(String uri) throws HTTPRequestException {
    String newUri = "";
    StringTokenizer st = new StringTokenizer(uri, "/ ", true);
    while (st.hasMoreTokens()) {
      String tok = st.nextToken();
      if (tok.equals("/"))
        newUri += "/";
      else if (tok.equals(" "))
        newUri += "%20";
      else {
        try {
          newUri += URLEncoder.encode(tok, "ASCII");
        }
        catch (UnsupportedEncodingException e) {
          throw new InternalServerErrorException("Problems to encode the new URI.", e);
        }
      }
    }
    return newUri;
  }
}
