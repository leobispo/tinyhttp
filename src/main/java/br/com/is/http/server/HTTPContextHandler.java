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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.is.http.server.mediatype.ApplicationXwwwFormURLEncode;
import br.com.is.http.server.mediatype.HTTPMediaType;
import br.com.is.http.server.mediatype.MultipartFormData;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.ReaderListener;

public final class HTTPContextHandler implements Runnable {
  private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

  private static final int UNSUPORTED_MEDIA_TYPE_ERROR      = 415;
  private static final int LENGTH_REQUIRED_ERROR            = 411;
  
  private static final String M_DIGEST_ALGORITHM            = "MD5";
  
  private final static String SESSION_COOKIE_NAME           = "ISSESSIONID";
  
  private static final String CONTENT_LENGTH                = "content-length";
  private static final String CONTENT_TYPE                  = "content-type";
  
  private static final String MULTIPART_FORM_DATA           = "multipart/form-data";
  private static final String APPLICATION_X_FORM_URL_ENCODE = "application/x-www-form-urlencoded";
  
  private enum OutputType { NONE, OUTPUT_STREAM, PRINT_WRITER };

  private HTTPSession                                  session = null;
  
  private final HTTPRequest.RequestMethod              method;
  private final String                                 uri;
  private final HTTPContext                            context;
  private final HTTPChannel                            channel;
  private final EventLoop                              manager;
  private final ConcurrentHashMap<String, HTTPSession> sessions;
  private final Hashtable<String, Cookie>              requestCookies;
  private final Hashtable<String, String>              requestHeader;
  private final Hashtable<String, String>              params;
  private final ReaderListener                         keepAlive;
  
  private final AtomicInteger                         responseStatus  = new AtomicInteger(200);
  private final List<Cookie>                          responseCookies = new ArrayList<>();
  private final Hashtable<String, String>             responseHeader  = new Hashtable<>();
  
  private final HTTPOutputStream                      os;
  
  private static final Map<String, HTTPMediaType>     mediaTypes = new Hashtable<>();
  static {
    mediaTypes.put(MULTIPART_FORM_DATA          , new MultipartFormData()           );
    mediaTypes.put(APPLICATION_X_FORM_URL_ENCODE, new ApplicationXwwwFormURLEncode());
  }
    
  public HTTPContextHandler(final HTTPRequest.RequestMethod method, final String uri, final HTTPContext context, final HTTPChannel channel, final EventLoop manager,
    final ConcurrentHashMap<String, HTTPSession> sessions, final Hashtable<String, Cookie> cookies, final Hashtable<String, String> header,
    final Hashtable<String, String> params, final HTTPOutputStream os, final ReaderListener keepALive) {
    this.method         = method;
    this.uri            = uri;
    this.context        = context;
    this.channel        = channel;
    this.manager        = manager;
    this.sessions       = sessions;
    this.requestCookies = cookies;
    this.requestHeader  = header;
    
    if (params == null)
      this.params = new Hashtable<>();
    else
      this.params = params;
    
    this.os             = os;
    this.keepAlive      = keepALive;
    
    Cookie sessionCookie = requestCookies.get(SESSION_COOKIE_NAME);
    if (sessionCookie != null)
      session = sessions.get(sessionCookie.getValue());
    else
      session = null;
    
    os.setResponseCookies(responseCookies);
    os.setResponseHeader(responseHeader);
    os.setResponseStatus(responseStatus);

    manager.unregisterReaderListener(channel.getSocketChannel());
    manager.unregisterWriterListener(channel.getSocketChannel());
  }

  @Override
  public void run() {

/** TODO: Implement what must be implemented!!
  general-header = Cache-Control            ; Section 14.9
                 | Connection               ; Section 14.10
                 | Date                     ; Section 14.18
                 | Pragma                   ; Section 14.32
                 | Trailer                  ; Section 14.40
                 | Transfer-Encoding        ; Section 14.41
                 | Upgrade                  ; Section 14.42
                 | Via                      ; Section 14.45
                 | Warning                  ; Section 14.46

  request-header = Accept                   ; Section 14.1
                 | Accept-Charset           ; Section 14.2
                 | Accept-Encoding          ; Section 14.3
                 | Accept-Language          ; Section 14.4
                 | Authorization            ; Section 14.8
                 | Expect                   ; Section 14.20
                 | From                     ; Section 14.22
                 | Host                     ; Section 14.23
                 | If-Match                 ; Section 14.24
                 | If-Modified-Since        ; Section 14.25
                 | If-None-Match            ; Section 14.26
                 | If-Range                 ; Section 14.27
                 | If-Unmodified-Since      ; Section 14.28
                 | Max-Forwards             ; Section 14.31
                 | Proxy-Authorization      ; Section 14.34
                 | Range                    ; Section 14.35
                 | Referer                  ; Section 14.36
                 | TE                       ; Section 14.39
                 | User-Agent               ; Section 14.43
 */
    switch (method) {
      case GET:
        context.doGet(new HTTPRequestImpl(new HTTPInputStream(channel, manager)), new HTTPResponseImpl());
      break;
      case HEAD:
        os.setIgnoreData(true);
        context.doHead(new HTTPRequestImpl(new HTTPInputStream(channel, manager)), new HTTPResponseImpl());
      break;
      case POST:
        processPOST();
      break;
      case PUT:
        context.doPut(null, null);
      break;
      case DELETE:
        context.doDelete(null, null);
      break;
      case TRACE:
        context.doTrace(null, null);
      break;
      case OPTIONS:
        context.doOptions(null, null);
      break;
    }

    os.flush();

    if (keepAlive != null) {
      keepAlive.read(channel.getSocketChannel(), manager);
    }
    else {
      try {
        channel.close();
      }
      catch (IOException e) {
        if (LOGGER.isLoggable(Level.WARNING))
          LOGGER.log(Level.WARNING, "Problems to close the HTTP Channel", e);
      }
    }
  }

  private void processPOST() {
    long contentLength = 0;
    try {
      String length = requestHeader.get(CONTENT_LENGTH);

      if (length == null) {
        os.sendError(LENGTH_REQUIRED_ERROR);
        return;
      }

      //TODO: Check if the Content-length is the size that I am willing to process. - implement it using annotation!!
      contentLength = Long.parseLong(length);
    }
    catch (NumberFormatException e) {
      os.sendError(LENGTH_REQUIRED_ERROR);
      return;
    }

    String type = requestHeader.get(CONTENT_TYPE);
    
    if (type == null) {
      os.sendError(UNSUPORTED_MEDIA_TYPE_ERROR);
      return;
    }

    String parameter = null;
    int idx = type.indexOf(';');
    if (idx != -1) {
      parameter = type.substring(idx + 1).trim();
      type      = type.substring(0, idx).trim().toLowerCase();
    }
    
    HTTPMediaType mediaType = mediaTypes.get(type);
    if (mediaType != null) {
      mediaType.process(context, new HTTPRequestImpl(new HTTPInputStream(channel, manager, contentLength)),
        new HTTPResponseImpl(), parameter, params);
      }
    else {
      os.sendError(UNSUPORTED_MEDIA_TYPE_ERROR);
      return;
    }
  }
  
  /**
   * Implementation of HTTP Request interface. It will be used by the HTTPContext to receive all information parsed by the
   * request handler.
   * 
   * @author Leonardo Bispo de Oliveira.
   *
   */
  private class HTTPRequestImpl implements HTTPRequest {
    private final HTTPInputStream is;
    
    private List<Cookie> cookies = null;
    
    public HTTPRequestImpl(final HTTPInputStream is) {
      this.is = is;
    }
     
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
      if (cookies == null)
        cookies = new ArrayList<>(requestCookies.values());
      
      return cookies;
    }
    
    @Override
    public long getDateHeader(final String name) {
      return 0; //TODO: Implement ME!!
    }
    
    @Override
    public String getHeader(final String name) {
      return requestHeader.get(name);
    }
    
    @Override
    public Enumeration<String> getHeaderNames() {
      return requestHeader.keys();
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
      return ""; //TODO: Implement ME!!
    }
    
    @Override
    public String getRequestedSessionId() {
      return ""; //TODO: Implement ME!!
    }
    
    @Override
    public String getRequestURI() {
      return uri; //TODO: Implement ME!!
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
        sessions.putIfAbsent(session.getId(), session);

        responseCookies.add(new Cookie(SESSION_COOKIE_NAME, session.getId()));
      }
      
      return session;
    }
    
    @Override
    public Principal getUserPrincipal() {
      return null; //TODO: Implement ME!!
    }
    
    @Override
    public boolean isUserInRole(final String role) {
      return false; //TODO: Implement ME!!
    }
    
    @Override
    public void login(final String username, final String password) {
      //TODO: Implement ME!!
    }
    
    @Override
    public void logout() {
      //TODO: Implement ME!!
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

    @Override
    public InputStream getInputStream() {
      return is;
    }
    
    /**
     * Copied from: http://www.java2s.com/Code/Java/Development-Class/SessionIDgenerator.htm
     * 
     * @return
     * 
     */
    private String generateUID() {
      final Random random = new SecureRandom();
      random.setSeed(System.currentTimeMillis());
      
      final int length = 30;
      byte[] buffer = new byte[length];

      StringBuffer reply = new StringBuffer();

      MessageDigest digest = null;
      try {
        digest = MessageDigest.getInstance(M_DIGEST_ALGORITHM);
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

  private final class HTTPResponseImpl implements HTTPResponse {
    private PrintWriter writer = new PrintWriter(os);
    private OutputType type    = OutputType.NONE;
    
    @Override
    public void addCookie(Cookie cookie) {
      responseCookies.add(cookie);
    }

    @Override
    public void addHeader(String name, String value) {
      responseHeader.put(name, value);
    }

    @Override
    public boolean containsHeader(String name) {
      return responseHeader.containsKey(name);
    }

    @Override
    public String getHeader(String name) {
      return responseHeader.get(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      return responseHeader.keys();
    }

    @Override
    public int getStatus() {
      return responseStatus.get();
    }

    @Override
    public void sendRedirect(String location) {
    }

    @Override
    public void setStatus(int sc) {
      responseStatus.set(sc);
    }

    @Override
    public OutputStream getOutputStream() {
      if (type != OutputType.PRINT_WRITER) {
        type = OutputType.OUTPUT_STREAM;
        return os;
      }
      
      return null;
    }

    @Override
    public PrintWriter getWriter() {
      if (type != OutputType.OUTPUT_STREAM) {
        type = OutputType.PRINT_WRITER;
        return writer;
      }
      
      return null;
    }
    
    @Override
    public void sendError(int error) {
      os.sendError(error);
    }
  }
}
