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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.is.http.server.encoder.Encoder;
import br.com.is.http.server.encoder.GZIPEncoder;
import br.com.is.http.server.exception.HTTPRequestException;
import br.com.is.http.server.mediatype.ApplicationXwwwFormURLEncode;
import br.com.is.http.server.mediatype.HTTPMediaType;
import br.com.is.http.server.mediatype.MultipartFormData;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.ReaderListener;

//TODO: All errors must return an HTML file.
final class HTTPContextHandler implements Runnable {
  private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

  private static int TIME_TO_EXPIRE_SESSION_MS              = 600000;
  private static final String M_DIGEST_ALGORITHM            = "MD5";
  
  private static final String SESSION_COOKIE_NAME           = "ISSESSIONID";
  
  private static final String ACCEPT_ENCODING               = "accept-encoding";
  private static final String CONTENT_LENGTH                = "content-length";
  private static final String CONTENT_TYPE                  = "content-type";
  
  private static final String MULTIPART_FORM_DATA           = "multipart/form-data";
  private static final String APPLICATION_X_FORM_URL_ENCODE = "application/x-www-form-urlencoded";
  
  private static final String GZIP_ENCODER                  = "gzip";
  
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
  private final Hashtable<String, Part>                requestParts = new Hashtable<>();
  private final Hashtable<String, String>              params;
  private final ReaderListener                         keepAlive;
  
  private final AtomicInteger                         responseStatus  = new AtomicInteger(200);
  private final List<Cookie>                          responseCookies = new ArrayList<>();
  private final Hashtable<String, String>             responseHeader  = new Hashtable<>();
  
  private final HTTPOutputStream                      os;
  
  private static final Map<String, HTTPMediaType>     mediaTypes = new Hashtable<>();
  private static final Map<String, Encoder>           encoders = new Hashtable<>();

  static {
    mediaTypes.put(MULTIPART_FORM_DATA          , new MultipartFormData()           );
    mediaTypes.put(APPLICATION_X_FORM_URL_ENCODE, new ApplicationXwwwFormURLEncode());
  }
  
  static {
    encoders.put(GZIP_ENCODER, new GZIPEncoder());
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
    if (sessionCookie != null) {
      session = sessions.get(sessionCookie.getValue());
      if (session != null) {
        manager.updateTimer(TIME_TO_EXPIRE_SESSION_MS, session);
        session.setLastAccessTime(System.currentTimeMillis());
      }
    }
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
    if (requestHeader.containsKey(ACCEPT_ENCODING) && context.useCodeEncoding()) {
      List<HTTPEncoder> list = parseEncoder(requestHeader.get(ACCEPT_ENCODING));
      for (HTTPEncoder encoder : list) {
        Encoder toUse = encoders.get(encoder.value);

        if (toUse != null) {
          try {
            toUse = toUse.getClass().newInstance();
            os.setEncoder(toUse);
            break;
          }
          catch (InstantiationException  | IllegalAccessException e) {
            if (LOGGER.isLoggable(Level.WARNING))
              LOGGER.log(Level.WARNING, "Problems to create a new instance for the Encoder: " + encoder.value);
          }
        }
      }
    }

    switch (method) {
      case HEAD:
        os.setIgnoreData(true);
      case GET:
        HTTPResponseImpl response = new HTTPResponseImpl();
        context.doGet(new HTTPRequestImpl(new HTTPInputStream(channel, manager)), response);
        if (response.type == OutputType.PRINT_WRITER)
          response.writer.flush();
      break;
      case POST:
        processPOST(); //TODO: Implement Transfer-Encoding:
      break;
      case PUT:
        context.doPut(null, null); //TODO: Implement ME!!
      break;
      case DELETE:
        context.doDelete(null, null); //TODO: Implement ME!!
      break;
      case TRACE:
        context.doTrace(null, null); //TODO: Implement ME!!
      break;
      case OPTIONS:
        context.doOptions(null, null); //TODO: Implement ME!!
      break;
    }

    os.flushCompressed();

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
        os.sendError(HTTPStatus.LENGTH_REQUIRED);
        return;
      }

      contentLength = Long.parseLong(length);
      if (contentLength > context.getMaxContentLenght()) {
        os.sendError(HTTPStatus.REQUEST_ENTITY_TOO_LARGE);
        return;
      }
    }
    catch (NumberFormatException e) {
      os.sendError(HTTPStatus.LENGTH_REQUIRED);
      return;
    }

    String type = requestHeader.get(CONTENT_TYPE);
    
    if (type == null) {
      os.sendError(HTTPStatus.UNSUPORTED_MEDIA_TYPE);
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
      try {
        final HTTPResponseImpl response = new HTTPResponseImpl();
        mediaType = mediaType.getClass().newInstance();
        mediaType.process(context, new HTTPRequestImpl(new HTTPInputStream(channel, manager, contentLength)),
          response, parameter, params, requestParts);
        
        if (response.type == OutputType.PRINT_WRITER)
          response.writer.flush();
      }
      catch (HTTPRequestException e) {
        if (LOGGER.isLoggable(Level.WARNING))
          LOGGER.log(Level.WARNING, e.getMessage(), e);
        
        os.sendError(e.getError());
      }
      catch (InstantiationException | IllegalAccessException e) {
        if (LOGGER.isLoggable(Level.WARNING))
          LOGGER.log(Level.WARNING, e.getMessage(), e);
        
        os.sendError(HTTPStatus.INTERNAL_SERVER_ERROR);
      }
    }
    else
      os.sendError(HTTPStatus.UNSUPORTED_MEDIA_TYPE);
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
    public List<Cookie> getCookies() {
      if (cookies == null)
        cookies = new ArrayList<>(requestCookies.values());
      
      return cookies;
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
      return requestParts.get(name);
    }
    
    @Override
    public String getRequestedSessionId() {
      if (session != null)
        return session.getId();
      
      return null;
    }
    
    @Override
    public String getRequestURI() {
      return uri;
    }
    
    @Override
    public StringBuffer getRequestURL() {
      final StringBuffer buffer = new StringBuffer();
      if (channel.isSSL())
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
        session = new HTTPSession(generateUID(), sessions);
        sessions.put(session.getId(), session);

        manager.registerTimer(TIME_TO_EXPIRE_SESSION_MS, session);
        responseCookies.add(new Cookie(SESSION_COOKIE_NAME, session.getId()));
      }
      
      return session;
    }

    @Override
    public String getParameter(final String name) {
      if (params == null)
        return null;
      
      return params.get(name);
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
    public void sendRedirect(String location) throws IllegalStateException {
      if (os.isHeaderCreated())
        throw new IllegalStateException("The header was already written to the channel");
      
      responseStatus.set(HTTPStatus.TEMPORARY_REDIRECT.getValue());
      responseHeader.clear();
      responseHeader.put("Location", location);
      
      // TODO: Maybe this should come from a template file.
      final StringBuilder sb = new StringBuilder();
      sb.append("<html>")
        .append("<head>")
        .append("<title>Moved</title>")
        .append("</head><body>")
        .append("<h1>Moved</h1>")
        .append("<p>This page has moved to <a href='")
        .append(location)
        .append("'>")
        .append(location)
        .append("</a>.</p></body>")
        .append("</html>");
      
      os.clear();
      
      final String page = sb.toString();
      os.write(page.getBytes(), 0, page.length());
      
      os.setIgnoreData(true);
    }

    @Override
    public void setStatus(HTTPStatus sc) {
      responseStatus.set(sc.getValue());
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
  }
  
  private class HTTPEncoder {
    public float  q = 1.0f;
    public String value;
  }
  
  /**
   * This method will parse the encoder list, returning a sorted list of it.
   * 
   * @param encoder String to be parsed.
   * 
   * @return Sorted list of encoders.
   * 
   */
  private List<HTTPEncoder> parseEncoder(final String encoder) {
    final List<HTTPEncoder> ret = new ArrayList<>();
    final StringTokenizer st = new StringTokenizer(encoder, ",");
    while (st.hasMoreTokens()) {
      final HTTPEncoder element = new HTTPEncoder();
      final String token = st.nextToken().trim();
      final int idx = token.indexOf(";q=");
      
      if (idx != -1) {
        element.q = Float.parseFloat(token.substring(idx + 3).trim());
        element.value = token.substring(0, idx).trim().toLowerCase();
      }
      else
        element.value = token.trim().toLowerCase();
      
      ret.add(element);
    }
    
    Collections.sort(ret, new Comparator<HTTPEncoder>() {
      @Override
      public int compare(HTTPEncoder o1, HTTPEncoder o2) {
        return (int) (o1.q - o2.q);
      }
    });
    
    return ret;
  }
}