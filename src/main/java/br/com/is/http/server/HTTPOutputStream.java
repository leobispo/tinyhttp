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
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.is.http.server.encoder.Encoder;
import br.com.is.nio.ByteBufferFifo;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.WriterListener;

final class HTTPOutputStream extends OutputStream implements WriterListener {
  private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

  private static final SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss z");
  static {
    fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
  }
  
  private final EventLoop   manager;
  private final HTTPChannel channel;
  
  private final Semaphore sem = new Semaphore(0);
  
  private ByteBuffer buffer   = null;
  
  private ByteBuffer           header = null;
  private final ByteBufferFifo fifo   = new ByteBufferFifo();
  
  private AtomicInteger             responseStatus  = null;
  private List<Cookie>              responseCookies = null;
  private Hashtable<String, String> responseHeader  = null;

  private boolean headerCreated = false;
  private boolean ignoreData    = false;

  private Encoder encoder       = null;
  
  public HTTPOutputStream(final HTTPChannel channel, final EventLoop manager) {
    this.channel = channel;
    this.manager = manager;

    manager.registerWriterListener(channel.getSocketChannel(), this);
  }

  @Override
  public void write(int b) throws IOException {
    if (ignoreData)
      return;

    for (;;) {
      ByteBuffer next = fifo.getWriteBuffer();
      if (next == null)
        return;
      
      try {
        next.putInt(b);
        break;
      }
      catch (BufferOverflowException e) {
        fifo.invalidateWriteBuffer();
      }
    }
  }

  @Override                                                              
  public void write(byte[] source, int offset, int length) {
    if (encoder != null) {
      try {
        encoder.compress(source, offset, length);
      }
      catch (IOException e) {
        throw new RuntimeException("Problems to compress data using an encoder.", e);
      }
    }
    else
      writeImpl(source, offset, length);
  }
  
  @Override
  public void flush() {
    if (!headerCreated) {
      final StringBuilder sb = new StringBuilder();
      sb.append("HTTP/1.1 ")
        .append(responseStatus.get())
        .append(' ')
        .append(getReasonPhrase(responseStatus.get()))
        .append("\r\n");
      
      if (responseHeader != null) {
        for (Map.Entry<String,String> entry : responseHeader.entrySet())
          sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
      }
      
      long date = System.currentTimeMillis();
      Timestamp timestamp = new Timestamp(date);
      
      sb.append("Date: ").append(fmt.format(timestamp)).append("\r\n");
      sb.append("Connection: close\r\n");

      if (encoder != null) {
        sb.append("Content-Encoding: ").append(encoder.getType()).append("\r\n");
        sb.append("Transfer-Encoding: chunked\r\n");
      }
      
      if (responseCookies != null) {
        for (Cookie cookie : responseCookies)
          sb.append("Set-Cookie: ").append(cookie).append("\r\n");
      }
      
      String header = sb.append("\r\n").toString();
      
      Charset charset = Charset.forName("ASCII");
      CharsetEncoder encoder = charset.newEncoder();
      try {
        this.header = encoder.encode(CharBuffer.wrap(header));
      }
      catch (CharacterCodingException e) {
        throw new RuntimeException("Problems To create a Header", e);
      }

      headerCreated = true;
    }
    
    if (encoder != null) {
      byte array[];
      try {
        array = encoder.flush();
      }
      catch (IOException ie) {
        throw new RuntimeException("Problems with the encoding method", ie);
      }
      
      if (array == null)
        return;
      
      String str = Integer.toHexString(array.length) + "\r\n";
      writeImpl(str.getBytes(), 0, str.length());
      writeImpl(array, 0, array.length);
      writeImpl("\r\n".getBytes(), 0, "\r\n".length());
    }

    write(channel.getSocketChannel(), manager);
    try {
      sem.acquire();
    }
    catch (InterruptedException e) {}
  }

  @Override
  public void write(final SelectableChannel ch, final EventLoop manager) {
    for (;;) {
      if (header != null) {
        buffer = header;
        header = null;
      }
      else if (buffer == null)
        buffer = fifo.getReadBuffer();

      if (buffer == null)
        break;
 
      try {
        channel.write(buffer);
      }
      catch (IOException e) {
        if (LOGGER.isLoggable(Level.WARNING))
          LOGGER.log(Level.WARNING, "Problems to write on the HTTP Channel", e);
        
        break;
      }
      
      if (buffer.hasRemaining())
        return;
      
      buffer = null;
    }

    sem.release();
  }
 
  public void setResponseStatus(AtomicInteger responseStatus) {
    this.responseStatus = responseStatus;
  }

  public void setResponseCookies(List<Cookie> responseCookies) {
    this.responseCookies = responseCookies;
  }

  public void setResponseHeader(Hashtable<String, String> responseHeader) {
    this.responseHeader = responseHeader;
  }
  
  public void setIgnoreData(boolean ignore) {
    ignoreData = ignore;
  }
  
  public void sendError(int error) {
    if (responseStatus == null)
      responseStatus = new AtomicInteger(error);
    else
      responseStatus.set(error);
    
    flush();
  }
  
  void flushCompressed() {
    flush();
    if (encoder != null) {
      writeImpl("0\r\n\r\n".getBytes(), 0, "0\r\n\r\n".length());
      write(channel.getSocketChannel(), manager);
    }
  }

  public void setEncoder(final Encoder encoder) {
    this.encoder = encoder;
  }

  private void writeImpl(byte[] source, int offset, int length) {
    if (ignoreData) 
      return;
    
    while (length > 0) {
      ByteBuffer next = fifo.getWriteBuffer();
      if (next == null)
        return;

      int bytesToWrite = length;
      if (next.remaining() < bytesToWrite)
        bytesToWrite = next.remaining();
      
      next.put(source, offset, bytesToWrite);
      offset += bytesToWrite;
      length -= bytesToWrite;
    }
  }

  void clear() {
    fifo.clear();  
  }
  
  boolean isHeaderCreated() {
    return headerCreated;
  }
  
  private String getReasonPhrase(int status) {
    switch (status) {
      case 100: return "Continue";
      case 101: return "Switching Protocols";
      case 200: return "OK";
      case 201: return "Created";
      case 202: return "Accepted";
      case 203: return "Non-Authoritative Information";
      case 204: return "No Content";
      case 205: return "Reset Content";
      case 206: return "Partial Content";
      case 300: return "Multiple Choices";
      case 301: return "Moved Permanently";
      case 302: return "Found";
      case 303: return "See Other";
      case 304: return "Not Modified";
      case 305: return "Use Proxy";
      case 307: return "Temporary Redirect";
      case 400: return "Bad Request";
      case 401: return "Unauthorized";
      case 402: return "Payment Required";
      case 403: return "Forbidden";
      case 404: return "Not Found";
      case 405: return "Method Not Allowed";
      case 406: return "Not Acceptable";
      case 407: return "Proxy Authentication Required";
      case 408: return "Request Time-out";
      case 409: return "Conflict";
      case 410: return "Gone";
      case 411: return "Length Required";
      case 412: return "Precondition Failed";
      case 413: return "Request Entity Too Large";
      case 414: return "Request-URI Too Large";
      case 415: return "Unsupported Media Type";
      case 416: return "Requested range not satisfiable";
      case 500: return "Internal Server Error";
      case 501: return "Not Implemented";
      case 502: return "Bad Gateway";
      case 503: return "Service Unavailable";
      case 504: return "Gateway Time-out";
      case 505: return "HTTP Version not supported";
      default: return "";
    }
  }
}