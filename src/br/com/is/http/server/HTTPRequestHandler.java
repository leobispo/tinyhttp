package br.com.is.http.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Hashtable;
import java.util.StringTokenizer;

final class HTTPRequestHandler {
  private enum RequestMethod { POST, GET, PUT, HEAD, DELETE, TRACE, CONNECT };
  private enum HeaderType { Method, Attribute, Body } 
  
  private static final int BUFFER_SIZE = 4096;
  
  private final HTTPChannel                    channel;
  private final Hashtable<String, HTTPContext> contexts;

  private RequestMethod                   method;
  private HeaderType                      type            = HeaderType.Method;

  private String                          mediaType       = null;
  private Hashtable<String, String>       mediaTypeParams = null;
  private String                          headerField     = "";

  private ByteBuffer                      buffer          = ByteBuffer.allocate(BUFFER_SIZE);  

  private Hashtable<String, String>       params          = null;
  private final Hashtable<String, String> header          = new Hashtable<>();
  


  //TODO: IMPLEMENT THE TIMEOUT!!
  
  /**
   * Constructor.
   * 
   * @param channel
   * @param contexts
   */
  HTTPRequestHandler(final HTTPChannel channel, final Hashtable<String, HTTPContext> contexts) {
    this.channel  = channel;
    this.contexts = contexts;
  }

  /**
   * 
   * @param sk
   * @throws IOException
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
        else if (header.containsKey("content-length") && method == RequestMethod.POST) {
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
            header.put(headerField.substring(0, idx).trim().toLowerCase(), headerField.substring(idx + 1).trim());
        }
        
        type = HeaderType.Body;
        return true;
      }
      
      if (type == HeaderType.Method) {
        final String method[] = line.split(" ");
        
        if (method.length != 3)
          return false; //TODO: THROW AN EXCEPTION

        if (method[0].equalsIgnoreCase("POST"))
          this.method = RequestMethod.POST;
        else if (method[0].equalsIgnoreCase("GET"))
          this.method = RequestMethod.GET;
        else if (method[0].equalsIgnoreCase("PUT"))
          this.method = RequestMethod.PUT;
        else if (method[0].equalsIgnoreCase("HEAD"))
          this.method = RequestMethod.HEAD;
        else if (method[0].equalsIgnoreCase("DELETE"))
          this.method = RequestMethod.DELETE;
        else if (method[0].equalsIgnoreCase("TRACE"))
          this.method = RequestMethod.TRACE;
        else if (method[0].equalsIgnoreCase("CONNECT"))
          this.method = RequestMethod.CONNECT;        

        header.put("Request-URI" , decodeUri(method[1]));
        header.put("HTTP-Version", method[2].trim());
        
        type = HeaderType.Attribute;
      }
      else if (line.indexOf(' ') != 0 && line.indexOf('\t') != 0) {
        if (!headerField.isEmpty()) {
          int idx = line.indexOf(':');
          if (idx != -1)
            header.put(line.substring(0, idx).trim().toLowerCase(), line.substring(idx + 1).trim());
        }
        headerField = line;
      }
      else
        headerField += line;
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
    //TODO: RETURN !!!
  }
  
  private void processPUT() {
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
   * @return Decoded URI.
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
}
