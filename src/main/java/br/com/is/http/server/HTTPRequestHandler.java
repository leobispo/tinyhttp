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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Class responsible to handle all the Requests. This class will be responsible to parse the HTTP information and generate
 * right calls to the HTTP contexts. If an error occurr or there is no HTTP contexts listening, an exception shall be thrown.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
final class HTTPRequestHandler {
  private enum HeaderType    { Method, Attribute, Body };
  
  private static final int BUFFER_SIZE = 4096;
  
  private final HTTPChannel                    channel;
  private final Hashtable<String, HTTPContext> contexts;

  private String                          uri             = null;
  private HTTPRequest.RequestMethod       method;
  private HeaderType                      type            = HeaderType.Method;

  private String                          mediaType       = null;
  private Hashtable<String, String>       mediaTypeParams = null;
  private String                          headerField     = "";

  private ByteBuffer                      buffer          = ByteBuffer.allocate(BUFFER_SIZE);  

  final private List<Cookie>              cookies         = new ArrayList<>();
  private Hashtable<String, String>       params          = null;
  private final Hashtable<String, String> header          = new Hashtable<>();

  /**
   * Constructor.
   * 
   * @param channel The HTTP channel handler. The HTTP channel support SSL Connection as well.
   * @param contexts All HTTP Contexts registered on HTTP Server class before the run method has been called.
   * 
   */
  HTTPRequestHandler(final HTTPChannel channel, final Hashtable<String, HTTPContext> contexts) {
    this.channel  = channel;
    this.contexts = contexts;
  }

  /**
   * Whenever a new event occurs in the AIO, this method will be called.
   * 
   * @param sk The key selected in the AIO.
   * 
   * @throws IOException
   * 
   */
  public void handle(final SelectionKey sk) throws IOException {   
    //TODO: MUST KILL THE SESSION AFTER X SECONDS... IF AN ERROR OCCURR, MUST CLOSE THE SESSION!!
    if (!channel.handshake(sk)) //TODO: SEND AN ERROR!! Throw an Exception!!
      return;

    final long r = channel.read(buffer);
    if (r > 0) {
      if (type != HeaderType.Body) {
        if (!readHeader()) {
          if (buffer.remaining() < (int) (buffer.capacity()) * 0.10) {
            if (buffer.capacity() == (BUFFER_SIZE * 2))
              return; //TODO: IMPLEMENT ME!! Throw an exception!!
            ByteBuffer bb = ByteBuffer.allocate(buffer.capacity() * 2);
            buffer.flip();
            bb.put(buffer);
            buffer = bb;
          }
        }
        else if (header.containsKey("content-length") && method == HTTPRequest.RequestMethod.POST) {
          int contentLength = Integer.parseInt(header.get("content-length"));
            
          ByteBuffer bb = ByteBuffer.allocate(contentLength);
          buffer.flip();
          bb.put(buffer);
          buffer = bb;
        }
      }
      
      if (type == HeaderType.Body) {
        switch (method) {
          case POST:
            if (buffer.remaining() == 0)
              processPOST();
          break;
          case GET:
            processGET();
          break;
          case PUT:
            processPUT();
          case HEAD:
            processHEAD();
          break;
          case DELETE:
            processDELETE();
          break;
          case TRACE:
            processTRACE();
          break;
          case CONNECT:
            processCONNECT();
          break;
          default:
            //TODO: RETURN AN ERROR!!
        }
      }
    }
  }

  /**
   * 
   * @return
   * @throws IOException
   */
  private boolean readHeader() throws IOException {
    if (type == HeaderType.Body)
      return true;
    
    for (;;) {
      final String line = readLine();
      if (line == null)
        return false;
      else if (line.isEmpty()) { 
        if (!headerField.isEmpty()) {
          int idx = headerField.indexOf(':');
          if (idx != -1)
            parseHeaderField(headerField.substring(0, idx).trim().toLowerCase(), headerField.substring(idx + 1).trim());
        }
        
        type = HeaderType.Body;
        return true;
      }
      
      if (type == HeaderType.Method) {
        final String method[] = line.split(" ");
        
        if (method.length != 3)
          return false; //TODO: THROW AN EXCEPTION

        if (method[0].equalsIgnoreCase("POST"))
          this.method = HTTPRequest.RequestMethod.POST;
        else if (method[0].equalsIgnoreCase("GET"))
          this.method = HTTPRequest.RequestMethod.GET;
        else if (method[0].equalsIgnoreCase("PUT"))
          this.method = HTTPRequest.RequestMethod.PUT;
        else if (method[0].equalsIgnoreCase("HEAD"))
          this.method = HTTPRequest.RequestMethod.HEAD;
        else if (method[0].equalsIgnoreCase("DELETE"))
          this.method = HTTPRequest.RequestMethod.DELETE;
        else if (method[0].equalsIgnoreCase("TRACE"))
          this.method = HTTPRequest.RequestMethod.TRACE;
        else if (method[0].equalsIgnoreCase("CONNECT"))
          this.method = HTTPRequest.RequestMethod.CONNECT;        

        uri = decodeUri(method[1]);
        header.put("HTTP-Version", method[2].trim());
        
        type = HeaderType.Attribute;
      }
      else if (line.indexOf(' ') != 0 && line.indexOf('\t') != 0) {
        if (!headerField.isEmpty()) {
          int idx = line.indexOf(':');
          if (idx != -1)
            parseHeaderField(headerField.substring(0, idx).trim().toLowerCase(), headerField.substring(idx + 1).trim());
        }
        headerField = line;
      }
      else
        headerField += line;
    }
  }
  
  private void parseHeaderField(final String key, final String value) {
    if (key.equals("cookie"))
      parseCookies(value);
    else
      header.put(key, value);
  }
  
  private void parseCookies(final String cookie) {
    final StringTokenizer st = new StringTokenizer(cookie, ";");
    if (st.hasMoreTokens()) {
      do {
        final String tmp = st.nextToken();
        int idx = tmp.indexOf('=');
        if (idx >= 0)
          cookies.add(new Cookie(tmp.substring(0, idx).trim(), tmp.substring(idx +1)));
      } while (st.hasMoreTokens());
    }
  }
  
  private void processPOST() {
    if (header.containsKey("content-type")) {
      parseContentType(header.get("content-type"));
    
      if (mediaType != null) {
        if (mediaType.equalsIgnoreCase("multipart/form-data")) {
          //TODO: Process a multipart information!!!
        }
        else if (mediaType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
          final int position = buffer.position();

          buffer.flip();
          
          final StringBuilder sb = new StringBuilder();
          for (int i = 0; i < position - 1; ++i) {
            if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
              buffer.get(); buffer.get();
              break;
            }
            
            sb.append((char) buffer.get());
          }
          if (buffer.hasRemaining())
            sb.append((char) buffer.get());
          
          parseParams(sb.toString());
          buffer.clear();
          
          final HTTPContext ctx = contexts.get(uri);
          if (ctx != null) {
            HTTPRequestImpl request = new HTTPRequestImpl();
            ctx.doPost(request, null); //TODO: CHANGE ME!!
          }
          else {
            //TODO: IMPLEMENT ME!!
          }
        }
        else {
          //TODO: Return an error!!
        }
      }
      else {
        //TODO: RETURN AN ERROR!!
      }
    }
    else {
      //TODO: RETURN AN ERROR!!
    }
  }
  
  private void processGET() {
    final HTTPContext ctx = contexts.get(uri);
    if (ctx != null) {
      HTTPRequestImpl request = new HTTPRequestImpl();
      ctx.doGet(request, null); //TODO: CHANGE ME!!
    }
    else {
      //TODO: IMPLEMENT ME!!
    }
  }
  
  private void processPUT() {
  }
  
  private void processHEAD() {
  }

  private void processDELETE() {
  }

  private void processTRACE() {
  }

  private void processCONNECT() {
  }
  
  private void parseParams(final String src) {
    final StringTokenizer st = new StringTokenizer(src, "&");
    if (st.hasMoreTokens()) {
      params = new Hashtable<>();
      do {
        final String tmp = st.nextToken();
        int idx = tmp.indexOf('=');
        if (idx >= 0)
          params.put(decodeHEXUri(tmp.substring(0, idx)).trim(), decodeHEXUri(tmp.substring(idx +1)));
      } while (st.hasMoreTokens());
    }
  }

  private void parseContentType(final String src) {
    final StringTokenizer st = new StringTokenizer(src, ";");
    if (st.hasMoreTokens()) {
      mediaType = st.nextToken().trim();
    
      if (st.hasMoreTokens()) {
        mediaTypeParams = new Hashtable<>();
        
        do {
          final String tmp = st.nextToken();
          int idx = tmp.indexOf('=');
          if (idx >= 0)
            mediaTypeParams.put(tmp.substring(0, idx).trim(), tmp.substring(idx + 1));
        } while (st.hasMoreTokens());
      }
    }
  }

  /**
   * Decode the URI string.
   * 
   * @param src URI to be decoded.
   * @return Decoded URI.
   */
  private String decodeUri(final String src) {
    final int idx = src.indexOf( '?' );
    if (idx >= 0) {
      parseParams(src.substring(idx + 1));
      return decodeHEXUri(src.substring(0, idx));
    }

    return decodeHEXUri(src);
  }
  
  /**
   * Decode the HEX URI data.
   * 
   * @param src Data to be decoded.
   * 
   * @return Decoded URI.
   * 
   */
  private String decodeHEXUri(final String src) {
    final StringBuilder sb = new StringBuilder();
    
    for (int i = 0; i < src.length(); ++i) {
      char ch = src.charAt(i);
      switch (ch) {
        case '+':
          sb.append(' ');
          break;
        case '%':
          sb.append((char) Integer.parseInt(src.substring(i + 1, i + 3), 16));
          i += 2;
          break;
        default:
          sb.append(ch);
      }
    }
    
    return sb.toString();
  }

  /**
   * Read a line from the buffer. For us, a read line is a set of octets terminated to "\r\n"
   * 
   * @return An set of octets excluding the "\r\n", or null.
   * 
   */
  private String readLine() {
    final int position = buffer.position();

    buffer.flip();
    buffer.mark();
    
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < position - 1; ++i) {
      if (buffer.get(i) == '\r' && buffer.get(i + 1) == '\n') {
        buffer.get(); buffer.get();
        buffer.compact();
        
        return sb.toString();
      }
      sb.append((char) buffer.get());
    }

    buffer.reset();
    buffer.compact();

    return null;
  }
  
  private class HTTPRequestImpl implements HTTPRequest {
/*    private final List<Cookie>              cookies;
    private final Hashtable<String, String> params;
    private final Hashtable<String, String> header;
    
    
    public HTTPRequestImpl(final List<Cookie> cookies, final Hashtable<String, String> params, final Hashtable<String, String> header) {
      this.cookies = cookies;
      this.params  = params;
      this.header  = header;
    }
*/
    
    public boolean authenticate(final HTTPResponse response) {
      return false;
    }
    
    public String getAuthType() {
      return "";
    }
    
    public String getContextPath() {
      return "";
    }
    
    public List<Cookie> getCookies() {
      return cookies;
    }
    
    public long getDateHeader(final String name) {
      return 0;
    }
    
    public String getHeader(final String name) {
      return header.get(name);
    }
    
    public Enumeration<String> getHeaderNames() {
      return header.keys();
    }

    public RequestMethod getMethod() {
      return method;
    }
    
    public Part getPart(final String name) {
      return null;
    }
    
    public String getPathInfo() {
      return "";
    }
    
    public String getPathTranslated() {
      return "";
    }
    
    public String getQueryString() {
      return "";
    }
    
    public String getRemoteUser() {
      return "";
    }
    
    public String getRequestedSessionId() {
      return "";
    }
    
    public String getRequestURI() {
      return uri;
    }
    
    public StringBuffer getRequestURL() {
      final StringBuffer buffer = new StringBuffer();
      if (channel.getSSLContext() != null)
        buffer.append("https://");
      else
        buffer.append("http://");

      buffer.append(channel.getSocketChannel().socket().getLocalAddress().getHostName());
      final int port = channel.getSocketChannel().socket().getLocalPort();
      if (port != 80 && port != 443)
        buffer.append(':').append(port);
      
      buffer.append(uri);
      
      return buffer;
    }
    
    public HTTPSession getSession() {
      return null;
    }
    
    public HTTPSession getSession(boolean create) {
      return null;
    }
    
    public Principal getUserPrincipal() {
      return null;
    }
    
    public boolean isRequestedSessionIdFromCookie() {
      return false;
    }
    
    public boolean isRequestedSessionIdFromURL() {
      return false;
    }
    
    public boolean isRequestedSessionIdValid() {
      return false;
    }
    
    public boolean isUserInRole(final String role) {
      return false;
    }
    
    public void login(final String username, final String password) {
    }
    
    public void logout() {
    }
    
    public String getParameter(final String name) {
      if (params == null)
        return null;
      
      return params.get(name);
    }
    
    public Map<String, String> getParameterMap() {
      return params;
    }
    
    public Enumeration<String> getParameterNames() {
      if (params == null)
        return null;
      
      return params.keys();
    }
  }
}
