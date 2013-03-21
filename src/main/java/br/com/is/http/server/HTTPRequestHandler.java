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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import br.com.is.http.server.exception.HTTPRequestException;

/**
 * Class responsible to handle all the Requests. This class will be responsible to parse the HTTP information and generate
 * right calls to the HTTP contexts. If an error occur or there is no HTTP contexts listening, an exception shall be thrown.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
final class HTTPRequestHandler {
  private enum HeaderType    { Method, Attribute, Body };
  
  private static final int BUFFER_SIZE = 4096;
  
  private final HTTPChannel                    channel;
  private final Hashtable<String, HTTPContext> contexts;
  private final Hashtable<String, HTTPSession> sessions;

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
   * @param sessions All HTTP Sessions registered on HTTP Server class.
   * 
   */
  HTTPRequestHandler(final HTTPChannel channel, final Hashtable<String, HTTPContext> contexts, final Hashtable<String, HTTPSession> sessions) {
    this.channel  = channel;
    this.contexts = contexts;
    this.sessions = sessions;
  }

  /**
   * Whenever a new event occurs in the AIO, this method will be called.
   * 
   * @param sk The key selected in the AIO.
   * 
   * @throws IOException, HTTPRequestException
   * 
   */
  public void handle(final SelectionKey sk) throws IOException, HTTPRequestException {   
    //TODO: MUST KILL THE SESSION AFTER X SECONDS... IF AN ERROR OCCURR, MUST CLOSE THE SESSION!!
    if (!channel.handshake(sk))
      throw new HTTPRequestException("Invalid request. Problems to execute the SSL Handshake.");

    final long r = channel.read(buffer);
    if (r > 0) {
      if (type != HeaderType.Body) {
        if (!readHeader()) {
          if (buffer.remaining() < (int) (buffer.capacity()) * 0.10) {
            if (buffer.capacity() == (BUFFER_SIZE * 2))
              throw new HTTPRequestException("Invalid request. Not possible to read the HTTP Header. Header is bigger than " + (BUFFER_SIZE * 2));

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
        final HTTPContext ctx = contexts.get(uri);
        if (ctx == null) {
          //TODO: IMPLEMENT ME!!
        }

        switch (method) {
          case GET:     processGET(ctx);     break;
          case PUT:     processPUT(ctx);     break;
          case HEAD:    processHEAD(ctx);    break;
          case DELETE:  processDELETE(ctx);  break;
          case TRACE:   processTRACE(ctx);   break;
          case CONNECT: processCONNECT(ctx); break;
          default:
            if (buffer.remaining() == 0) 
              processPOST(ctx); 
        }
      }
    }
  }

  /**
   * This method will parse the HTTP 1.1 header.
   * 
   * @return True if it is correctly parsed, otherwise false.
   * 
   * @throws IOException, HTTPRequestException
   *
   */
  private boolean readHeader() throws IOException, HTTPRequestException {
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
          throw new HTTPRequestException("Invalid Request. Cannot parse the Request method");

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
  
  /**
   * Helper to process the many types of POST Messages.
   * 
   * @param ctx HTTP Handler context.
   * 
   */
  private void processPOST(final HTTPContext ctx) {
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
          
          HTTPRequestImpl  request  = new HTTPRequestImpl();
          HTTPResponseImpl response = new HTTPResponseImpl();
          ctx.doPost(request, response);
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
  
  /**
   * Helper to process the many types of GET Messages.
   * 
   * @param ctx HTTP Handler context.
   * 
   */
  private void processGET(final HTTPContext ctx) {
    HTTPRequestImpl  request  = new HTTPRequestImpl();
    HTTPResponseImpl response = new HTTPResponseImpl();
    ctx.doGet(request, response);
  }
  
  /**
   * Helper to process the many types of PUT Messages.
   * 
   * @param ctx HTTP Handler context.
   *
   */
  private void processPUT(final HTTPContext ctx) {
  }
  
  /**
   * Helper to process the many types of HEAD Messages.
   * 
   * @param ctx HTTP Handler context.
   * 
   */
  private void processHEAD(final HTTPContext ctx) {
  }

  /**
   * Helper to process the many types of DELETE Messages.
   * 
   * @param ctx HTTP Handler context.
   * 
   */
  private void processDELETE(final HTTPContext ctx) {
  }

  /**
   * Helper to process the many types of TRACE Messages.
   *
   * @param ctx HTTP Handler context.
   *
   */
  private void processTRACE(final HTTPContext ctx) {
  }

  /**
   * Helper to process the many types of CONNECT Messages.
   *
   * @param ctx HTTP Handler context.
   * 
   */
  private void processCONNECT(final HTTPContext ctx) {
  }
  
  /**
   * Helper to check if the header field is a Cookie or a standard header field.
   * 
   * @param key Header Key.
   * @param value Header Value.
   * 
   */
  private void parseHeaderField(final String key, final String value) {
    if (key.equals("cookie"))
      parseCookies(value);
    else
      header.put(key, value);
  }
  
  /**
   * Parse a Cookie header and create a list of cookies to be used by the HTTP Context.
   * 
   * @param cookie List of cookies to be parsed.
   * 
   */
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
  
  /**
   * Parse the get/post params and add it to the params map.
   * 
   * @param src Data to be parsed.
   * 
   */
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

  /**
   * Parse the content type header and add it to the media type parameters map.
   * 
   * @param src Data to be parsed.
   * 
   */
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
   * 
   * @return Decoded URI.
   * 
   */
  private String decodeUri(final String src) {
    final int idx = src.indexOf('?');
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
  
  /**
   * Implementation of HTTP Request interface. It will be used by the HTTPContext to receive all information parsed by the
   * request handler.
   * 
   * @author Leonardo Bispo de Oliveira.
   *
   */
  private class HTTPRequestImpl implements HTTPRequest {
    private HTTPSession session = null;
    
    @Override
    public boolean authenticate(final HTTPResponse response) {
      return false;
    }
    
    @Override
    public AuthType getAuthType() {
      return AuthType.NONE_AUTH; //TODO: Implement ME!!
    }
    
    @Override
    public List<Cookie> getCookies() {
      return cookies;
    }
    
    @Override
    public long getDateHeader(final String name) {
      return 0; //TODO: Implement ME!!
    }
    
    @Override
    public String getHeader(final String name) {
      return header.get(name);
    }
    
    @Override
    public Enumeration<String> getHeaderNames() {
      return header.keys();
    }
    
    @Override
    public RequestMethod getMethod() {
      return method;
    }
    
    @Override
    public Part getPart(final String name) {
      return null; //TODO: Implement ME!!
    }

    @Override
    public String getRemoteUser() {
      return "";
    }
    
    @Override
    public String getRequestedSessionId() {
      return "";
    }
    
    @Override
    public String getRequestURI() {
      return uri;
    }
    
    @Override
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

    @Override
    public HTTPSession getSession() {
      if (session == null) {
        session = new HTTPSession(generateUID());
        sessions.put(session.getId(), session);
      }
      
      return session;
    }
    
    @Override
    public Principal getUserPrincipal() {
      return null;
    }
    
    @Override
    public boolean isUserInRole(final String role) {
      return false;
    }
    
    @Override
    public void login(final String username, final String password) {
    }
    
    @Override
    public void logout() {
    }
    
    @Override
    public String getParameter(final String name) {
      if (params == null)
        return null;
      
      return params.get(name);
    }
    
    @Override
    public Map<String, String> getParameterMap() {
      return params;
    }
    
    @Override
    public Enumeration<String> getParameterNames() {
      if (params == null)
        return null;
      
      return params.keys();
    }
    
    /**
     * Copied from: http://www.java2s.com/Code/Java/Development-Class/SessionIDgenerator.htm
     * 
     * @return
     */
    private String generateUID() {
      final Random random = new SecureRandom();
      random.setSeed(System.currentTimeMillis());
      
      final int length = 30;
      byte[] buffer = new byte[length];

      StringBuffer reply = new StringBuffer();

      MessageDigest digest = null;
      try {
        digest = MessageDigest.getInstance("MD5");
      } 
      catch (NoSuchAlgorithmException e) {
        return "";
      }
      
      int resultLenBytes = 0;
      while (resultLenBytes < length) {
        random.nextBytes(buffer);
        buffer = digest.digest(buffer);

        for (int j = 0; j < buffer.length && resultLenBytes < length; ++j) {
          byte b1 = (byte) ((buffer[j] & 0xf0) >> 4);
          if (b1 < 10)
            reply.append((char) ('0' + b1));
          else
            reply.append((char) ('A' + (b1 - 10)));

          byte b2 = (byte) (buffer[j] & 0x0f);
          if (b2 < 10)
            reply.append((char) ('0' + b2));
          else
            reply.append((char) ('A' + (b2 - 10)));

          ++resultLenBytes;
        }
      }
      return reply.toString();
    }
  }
  
  private class HTTPResponseImpl implements HTTPResponse {

    @Override
    public void addCookie(Cookie cookie) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void addDateHeader(String name, long date) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void addHeader(String name, String value) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void addIntHeader(String name, int value) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public boolean containsHeader(String name) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public String encodeRedirectUrl(String url) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String encodeUrl(String url) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getHeader(String name) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Collection<String> getHeaderNames() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Collection<String> getHeaders(String name) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int getStatus() {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public void sendError(int sc) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void sendError(int sc, String msg) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void sendRedirect(String location) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void setDateHeader(String name, long date) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void setHeader(String name, String value) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void setIntHeader(String name, int value) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void setStatus(int sc) {
      // TODO Auto-generated method stub 
    }
  }
}
