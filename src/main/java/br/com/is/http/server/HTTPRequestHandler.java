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
import java.nio.channels.SelectableChannel;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.is.http.server.exception.BadRequestException;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.ReaderListener;

/**
 * Class responsible to handle all the Requests. This class will be responsible to parse the HTTP information and generate
 * right calls to the HTTP contexts. If an error occur or there is no HTTP contexts listening, an exception shall be thrown.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
final class HTTPRequestHandler implements ReaderListener {
  private enum HeaderType    { METHOD, ATTRIBUTE, BODY };
  
  private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  
  private static final int BAD_REQUEST_ERROR              = 400;
  private static final int NOT_FOUND_ERROR                = 404;
  private static final int METHOD_NOT_ALLOWED_ERROR       = 405;
  private static final int REQUEST_ENTITY_TOO_LARGE_ERROR = 413;
  private static final int INTERNAL_SERVER_ERROR          = 500;
  private static final int BUFFER_SIZE                    = 4096;
  
  private static final String COOKIE = "cookie";

  private final EventLoop                              manager;
  private final HTTPChannel                            channel;
  private final Hashtable<String, HTTPContext>         contexts;
  private final ConcurrentHashMap<String, HTTPSession> sessions;

  private String                          uri             = null;
  private HeaderType                      type            = HeaderType.METHOD;
  private String                          headerField     = "";

  private ByteBuffer                      buffer          = ByteBuffer.allocate(BUFFER_SIZE);  

  private Hashtable<String, Cookie>       cookies         = new Hashtable<>();
  private Hashtable<String, String>       params          = null;
  private Hashtable<String, String>       header          = new Hashtable<>();
  private boolean                         keepAlive       = false;
  private HTTPRequest.RequestMethod       method;

  private HTTPOutputStream os;

  private static final String CONNECTION            = "connection";
  private static final String CONNECTION_KEEP_ALIVE = "keep-alive";
  
  /**
   * Constructor.
   * 
   * @param channel The HTTP channel handler. The HTTP channel support SSL Connection as well.
   * @param contexts All HTTP Contexts registered on HTTP Server class before the run method has been called.
   * @param sessions All HTTP Sessions registered on HTTP Server class.
   * 
   */
  HTTPRequestHandler(final HTTPChannel channel, final Hashtable<String, HTTPContext> contexts, final ConcurrentHashMap<String, HTTPSession> sessions, final EventLoop manager) {
    this.manager  = manager;
    this.channel  = channel;
    this.contexts = contexts;
    this.sessions = sessions;
    
    os = new HTTPOutputStream(channel, manager);
  }

  @Override
  public void read(final SelectableChannel ch, final EventLoop manager) { 
    if (!channel.handshake()) {
      sendError("Cannot execute the handshake", BAD_REQUEST_ERROR);
      return;
    }

    long length = 0;
    do {
      try {
        length = channel.read(buffer);
      }
      catch (IOException e) {
        sendError(INTERNAL_SERVER_ERROR, e);
        return;
      }

      if (length > 0) {
        if (keepAlive) {
          reset();
          manager.registerReaderListener(channel.getSocketChannel(), this);
          keepAlive = false;
        }
        
        if (type != HeaderType.BODY) {
          try {
            if (!readHeader()) {
              if (buffer.remaining() < (int) (buffer.capacity()) * 0.10) {
                if (buffer.capacity() == (BUFFER_SIZE * 2)) {
                  sendError("Invalid request. Not possible to read the HTTP Header. Header is bigger than "  + (BUFFER_SIZE * 2),
                      REQUEST_ENTITY_TOO_LARGE_ERROR);
                  return;
                }

                ByteBuffer bb = ByteBuffer.allocate(buffer.capacity() * 2);
                buffer.flip();
                bb.put(buffer);
                buffer = bb;
              }
            }
          }
          catch (BadRequestException be) {
            sendError(BAD_REQUEST_ERROR, be);
            return;
          }
          catch (IOException ie) {
            sendError(INTERNAL_SERVER_ERROR, ie);
            return;
          }
        }

        if (type == HeaderType.BODY) {
          final HTTPContext ctx = contexts.get(uri);
          if (ctx == null) {
            sendError("Cannot find the context for: " + uri, NOT_FOUND_ERROR);
            return;
          }

          buffer.flip();
          channel.setRemaining(buffer);
          manager.unregisterReaderListener(channel.getSocketChannel());

          keepAlive = header.get(CONNECTION).equalsIgnoreCase(CONNECTION_KEEP_ALIVE);
          manager.registerThreadListener(new HTTPContextHandler(method, uri, ctx, this.channel, manager, sessions,
            cookies, header, params, os, keepAlive ? this : null));

          return;
        }
      }
      else if (length < 0 || keepAlive) {
        try {
          channel.close();
        }
        catch (IOException e) {
          if (LOGGER.isLoggable(Level.WARNING))
            LOGGER.log(Level.WARNING, "Problems to close the channel", e);
        }
      } 
    } while (length > 0);
  }

  /**
   * This method will parse the HTTP 1.1 header.
   * 
   * @return True if it is correctly parsed, otherwise false.
   * 
   * @throws IOException, BadRequestException
   *
   */
  private boolean readHeader() throws IOException, BadRequestException {
    if (type == HeaderType.BODY)
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
        
        type = HeaderType.BODY;
        return true;
      }
      
      if (type == HeaderType.METHOD) {
        final String method[] = line.split(" ");
        
        if (method.length != 3)
          throw new BadRequestException("Invalid Request. Cannot parse the Request method");
        
        if (method[0].equalsIgnoreCase(HTTPRequest.RequestMethod.POST.name()))
          this.method = HTTPRequest.RequestMethod.POST;
        else if (method[0].equalsIgnoreCase(HTTPRequest.RequestMethod.GET.name()))
          this.method = HTTPRequest.RequestMethod.GET;
        else if (method[0].equalsIgnoreCase(HTTPRequest.RequestMethod.PUT.name()))
          this.method = HTTPRequest.RequestMethod.PUT;
        else if (method[0].equalsIgnoreCase(HTTPRequest.RequestMethod.HEAD.name()))
          this.method = HTTPRequest.RequestMethod.HEAD;
        else if (method[0].equalsIgnoreCase(HTTPRequest.RequestMethod.DELETE.name()))
          this.method = HTTPRequest.RequestMethod.DELETE;
        else if (method[0].equalsIgnoreCase(HTTPRequest.RequestMethod.TRACE.name()))
          this.method = HTTPRequest.RequestMethod.TRACE;
        else if (method[0].equalsIgnoreCase(HTTPRequest.RequestMethod.OPTIONS.name()))
          this.method = HTTPRequest.RequestMethod.OPTIONS;
        else
          sendError("Request method not recognized", METHOD_NOT_ALLOWED_ERROR);

        uri = decodeUri(method[1]);
        header.put("HTTP-Version", method[2].trim());
        
        type = HeaderType.ATTRIBUTE;
      }
      else if (line.indexOf(' ') != 0 && line.indexOf('\t') != 0) {
        if (!headerField.isEmpty()) {
          int idx = headerField.indexOf(':');
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
   * Write an error message to the socket and close the communication.
   * 
   * @param message
   * @param error
   */
  private void sendError(final String message, int error) {
    if (LOGGER.isLoggable(Level.WARNING))
      LOGGER.log(Level.WARNING, message);
    
    os.sendError(error);
    try {
      channel.close();
    }
    catch (IOException e) {
      if (LOGGER.isLoggable(Level.WARNING))
        LOGGER.log(Level.WARNING, "Problems to close the channel", e);
    }
  }
  
  /**
   * Write an error message to the socket and close the communication.
   * 
   * @param error
   * @param e
   */
  private void sendError(int error, Exception e) {
    if (LOGGER.isLoggable(Level.WARNING))
      LOGGER.log(Level.WARNING, e.getMessage(), e);
    
    os.sendError(error);
    try {
      channel.close();
    }
    catch (IOException ioe) {
      if (LOGGER.isLoggable(Level.WARNING))
        LOGGER.log(Level.WARNING, "Problems to close the channel", ioe);
    }
  }

  /**
   * Helper to check if the header field is a Cookie or a standard header field.
   * 
   * @param key Header Key.
   * @param value Header Value.
   * 
   */
  private void parseHeaderField(final String key, final String value) {
    if (key.equals(COOKIE))
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
        if (idx >= 0) {
          String key = tmp.substring(0, idx).trim();
          cookies.put(key, new Cookie(key, tmp.substring(idx +1)));
        }
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

    System.out.println(sb.toString());
    buffer.reset();
    buffer.compact();

    return null;
  }
  
  /**
   * Restart this class to be used when the connection is keep-alive.
   * 
   */
  private void reset() {
    uri         = null;
    type        = HeaderType.METHOD;
    headerField = "";
    buffer      = ByteBuffer.allocate(BUFFER_SIZE);  
    cookies     = new Hashtable<>();
    params      = null;
    header      = new Hashtable<>();
    os          = new HTTPOutputStream(channel, manager);
  }
}
