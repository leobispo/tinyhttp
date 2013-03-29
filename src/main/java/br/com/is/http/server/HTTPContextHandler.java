package br.com.is.http.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;

import br.com.is.http.server.mediatype.ApplicationXwwwFormURLEncode;
import br.com.is.http.server.mediatype.HTTPMediaType;
import br.com.is.http.server.mediatype.MultipartFormData;
import br.com.is.nio.ByteBufferInputStream;
import br.com.is.nio.ByteBufferOutputStream;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.ReaderListener;
import br.com.is.nio.listener.WriterListener;

public class HTTPContextHandler implements Runnable {
 //private enum OutputType    { NONE, OUTPUT_STREAM, PRINT_WRITER };
  private final HTTPRequest.RequestMethod      method;
  private final String                         uri;
  private final HTTPContext                    context;
  private final HTTPChannel                    channel;
  private final EventLoop                      manager;
  private final Hashtable<String, HTTPSession> sessions;
  private final List<Cookie>                   cookies;
  private final Hashtable<String, String>      header;
  private final Hashtable<String, String>      params;
  
  private final HTTPInputStream                is;
  private final HTTPOutputStream               os;
  
  private static final String M_DIGEST_ALGORITHM            = "MD5";
  
  private static final String MULTIPART_FORM_DATA           = "multipart/form-data";
  private static final String APPLICATION_X_FORM_URL_ENCODE = "application/x-www-form-urlencoded";
  
  private static final Map<String, HTTPMediaType> mediaTypes = new Hashtable<>();
  static {
    mediaTypes.put(MULTIPART_FORM_DATA          , new MultipartFormData());
    mediaTypes.put(APPLICATION_X_FORM_URL_ENCODE, new ApplicationXwwwFormURLEncode());
  }
    
  public HTTPContextHandler(final HTTPRequest.RequestMethod method, final String uri, final HTTPContext context, final ByteBuffer buffer, final HTTPChannel channel, 
    final EventLoop manager, final Hashtable<String, HTTPSession> sessions, final List<Cookie> cookies, final Hashtable<String, String> header,
    final Hashtable<String, String> params) {
    this.method   = method;
    this.uri      = uri;
    this.context  = context;
    this.channel  = channel;
    this.manager  = manager;
    this.sessions = sessions;
    this.cookies  = cookies;
    this.header   = header;
    this.params   = params;
    
    manager.unregisterReaderListener(channel.getSocketChannel());
    manager.unregisterWriterListener(channel.getSocketChannel());

    is = new HTTPInputStream(channel.getSocketChannel(), manager, buffer); //TODO: JUST CREATE WHEN YOU ARE SURE YOU WILL USE IT!
    os = new HTTPOutputStream(channel.getSocketChannel(), manager);
  }

  @Override
  public void run() {
    //TODO: IF THE CONTENT-LENGTH IS TOO BIG OR NO CONTENT-LENGTH, FIRST PARSE THE DATA AND STORE IT ON A TEMP FILE - IF IT IS MULTIPART - YOU MUST DO IT AS WELL
    switch (method) {
      case GET:
        context.doGet(new HTTPRequestImpl(), null);
      break;
      case HEAD:
        context.doHead(new HTTPRequestImpl(), null);
        //TODO: JUST WRITE THE HEAD, NOT THE BODY!!
      break;
      case POST:
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      try {
        String line = reader.readLine();
        System.out.println(line);
      }
      catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
        //context.doPost(null, null);
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
      case CONNECT:
        context.doConnect(null, null);
      break;
      case OPTIONS:
        context.doOptions(null, null);
      break;
      default:
        //TODO: IMPLEMENT ME!!
      break;
    }

    try {
      channel.close();
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
    }
  }

  private static class HTTPInputStream extends ByteBufferInputStream {

    public HTTPInputStream(final SelectableChannel channel, final EventLoop manager, final ByteBuffer buffer) {
      super(channel, manager, buffer);
      // TODO Auto-generated constructor stub
    }
  }
  
  private static class HTTPOutputStream extends ByteBufferOutputStream {

    public HTTPOutputStream(SelectableChannel channel, EventLoop manager) {
      super(channel, manager);
      // TODO Auto-generated constructor stub
    }
  }
  
/*
  private void processPOST(final HTTPContext ctx, final EventLoop manager) throws UnsuportedMediaTypeException, BadRequestException {
    if (header.containsKey(CONTENT_TYPE)) {
      parseContentType(header.get(CONTENT_TYPE));
    
      if (mediaType != null) {
        if (mediaType.equalsIgnoreCase(MULTIPART_FORM_DATA)) {
          //TODO: Process a multipart information!!!
        }
        else if (mediaType.equalsIgnoreCase(APPLICATION_X_FORM_URL_ENCODE)) {
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
          ctx.doPost(request, response);
        }
        else
          throw new UnsuportedMediaTypeException("Media type " + mediaType + " not supported");
      }
      else
        throw new BadRequestException("The header does not contains the media-type field");
    }
 
    throw new BadRequestException("The header does not contains the content-type field");
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
*/
  
  
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
        sessions.put(session.getId(), session);
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

/*
  private class HTTPOutputStream extends ByteBufferOutputStream {
    private final HTTPResponseImpl response;
    
    public HTTPOutputStream(HTTPResponseImpl response, HTTPChannel channel, EventLoop manager) {
      super(channel.getSocketChannel(), manager);
      this.response = response;
    }

    //TODO: IMPLEMENT ME IF IT IS HTTPS!!
    
    @Override
    public void write(final SelectableChannel channel, final EventLoop manager) {
      if (response.writeHeader())
        super.write(channel, manager);
      else
        manager.registerWriterListener(channel, this);
    }
  }

  private class HTTPResponseImpl implements HTTPResponse {
    private int status = 200;

    private OutputType outputType = OutputType.NONE;
    
    private final HTTPOutputStream          output;
    private final PrintWriter               writer;
    private final List<Cookie>              cookies = new ArrayList<>(); 
    private final Hashtable<String, String> header  = new Hashtable<>();
   
    private boolean headerWritten = false;
    
    public HTTPResponseImpl(final EventLoop manager) {
      output = new HTTPOutputStream(this, channel, manager);
      writer = new PrintWriter(output, true);
    }
    
    @Override
    public void addCookie(Cookie cookie) {
      cookies.add(cookie);      
    }

    @Override
    public void sendRedirect(String location) {
      setStatus(307);
      // TODO Auto-generated method stub
    }

    @Override
    public void addHeader(String name, String value) {
      header.put(name, value);
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
    public boolean containsHeader(String name) {
      return header.containsKey(name);
    }

    @Override
    public void setStatus(int sc) {
      status = sc;
    }
    
    @Override
    public int getStatus() {
      return status;
    }

    @Override
    public OutputStream getOutputStream() {
      if (outputType == OutputType.PRINT_WRITER)
        return null;
      
      outputType = OutputType.OUTPUT_STREAM;
      
      return output;
    }

    @Override
    public PrintWriter getWriter() {
      if (outputType == OutputType.OUTPUT_STREAM)
        return null;
      
      outputType = OutputType.PRINT_WRITER;
      return writer;
    }
    
    public boolean writeHeader() {
      if (!headerWritten) {
        buffer.flip();
        buffer.clear();
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP/1.1 ").append(200).append(" OK\r\n\r\n"); //TODO: CHANGE THIS!!
        buffer.put(builder.toString().getBytes());
        buffer.flip();
        
        headerWritten = true;
      }
      
      if (buffer.remaining() != 0) {
        try {
          channel.getSocketChannel().write(buffer);
          if (buffer.remaining() != 0)
            return false;
        }
        catch (IOException e) {
          e.printStackTrace(); // TODO Auto-generated catch block
        } 
      }
      
      return true;
    }
    
  }
*/
  
}
