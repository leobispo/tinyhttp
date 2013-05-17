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
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.reflections.Reflections;

import br.com.is.http.server.annotation.Context;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.AcceptListener;

/**
 * Provides a simple high-level asynchronous Http server API, which can be used to build embedded HTTP servers.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
public final class HTTPServer implements Runnable, AcceptListener {
  public enum Type { HTTP, HTTPS }
  
  private SSLContext sslContext;
  
  private final static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  
  private final ConcurrentHashMap<String, HTTPSession> sessions      = new ConcurrentHashMap<>();
  private final EventLoop                              loop;
  private final Type                                   type;
  private final File                                   sslCertificate;
  private final String                                 passphrase;
  private final InetSocketAddress                      addr;
  private final int                                    backlog;
  private final Hashtable<String, HTTPContext>         contexts       = new Hashtable<>();
  private boolean                                      running        = false;
  private ServerSocketChannel                          serverChannel  = null;
  
  /**
   * Constructor.
   * 
   * @param type HTTP Method Type.
   * @param addr Address to listen on.
   * @param backlog The socket backlog. If this value is less than or equal to zero, then a system default value is used.
   * @param staticLocation Path where the application will look for static contents.
   * @param sslCertificate File containing the SSL Certificate.
   * @param passphrase Certificate passphrase.
   * 
   * @throws IOException
   * 
   */
  private HTTPServer(Type type, final InetSocketAddress addr, final int backlog, final String staticLocation,
    final File sslCertificate, final String passphrase) throws IOException {
    this.addr            = addr;
    this.type            = type;
    this.backlog         = backlog;
    this.sslCertificate  = sslCertificate;
    this.passphrase      = passphrase;
    this.loop            = new EventLoop(backlog);

    if (staticLocation != null)
      contexts.put("/", new HTTPStaticContext(staticLocation));
  }
  
  /**
   * Constructor.
   * 
   * @param addr Address to listen on.
   * @param backlog The socket backlog. If this value is less than or equal to zero, then a system default value is used.
   * @param staticLocation Path where the application will look for static contents.
   * @param sslCertificate File containing the SSL Certificate.
   * @param passphrase Certificate passphrase.
   * 
   * @throws IOException
   * 
   */
  public HTTPServer(final InetSocketAddress addr, final int backlog, final String staticLocation, final File sslCertificate,
    final String passphrase) throws IOException {
    this(Type.HTTPS, addr, backlog, staticLocation, sslCertificate, passphrase);
  }
  
  /**
   * Constructor.
   * 
   * @param addr Address to listen on.
   * @param backlog The socket backlog. If this value is less than or equal to zero, then a system default value is used.
   * 
   * @throws IOException
   * 
   */
  public HTTPServer(final InetSocketAddress addr, final int backlog) throws IOException {
    this(Type.HTTP, addr, backlog, null, null, null);
  }
  
  /**
   * Constructor.
   * 
   * @param addr Address to listen on.
   * @param backlog The socket backlog. If this value is less than or equal to zero, then a system default value is used.
   * @param staticLocation Path where the application will look for static contents.
   * 
   * @throws IOException
   * 
   */
  public HTTPServer(final InetSocketAddress addr, final int backlog, final String staticLocation) throws IOException {
    this(Type.HTTP, addr, backlog, staticLocation, null, null);
  }
  
  /**
   * Constructor.
   * 
   * @param addr Address to listen on.
   * @param backlog The socket backlog. If this value is less than or equal to zero, then a system default value is used.
   * @param sslCertificate File containing the SSL Certificate.
   * @param passphrase Certificate passphrase.
   * 
   * @throws IOException
   * 
   */
  public HTTPServer(final InetSocketAddress addr, final int backlog, final File sslCertificate, 
    final String passphrase) throws IOException {
    this(Type.HTTPS, addr, backlog, null, sslCertificate, passphrase);
  }
  
  /**
   * Returns the address this server is listening on.
   * 
   * @return The address/port number the server is listening on.
   * 
   */
  public InetSocketAddress getAddress() {
    return addr;
  }
  
  /**
   * Returns the HTTP connection type (HTTP or HTTPS).
   * 
   * @return HTTP connection type (HTTP or HTTPS).
   * 
   */
  public Type getType() {
    return type;
  }
  
  /**
   * Runnable run method. Can be used by a thread, or directly.
   * 
   */
  @Override
  public void run() {
    try {
      Reflections reflections = new Reflections("");
      Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(br.com.is.http.server.annotation.Context.class);
      for (Class<?> clazz : annotated) {
        final Object newInstance    = (Object) clazz.newInstance();
        final String urlPattern     = getClassAnnotationStringValue(clazz , Context.class, "urlPattern");
        final String tempDirectory  = getClassAnnotationStringValue(clazz , Context.class, "tempDirectory");
        final Long maxContentLength = getClassAnnotationLongValue(clazz   , Context.class, "maxContentLength");
        final Boolean acceptEncode  = getClassAnnotationBooleanValue(clazz, Context.class, "acceptEncode");

        HTTPAnnotatedContext ctx = new HTTPAnnotatedContext(newInstance,
          getMethod(clazz, br.com.is.http.server.annotation.GET.class),
          getMethod(clazz, br.com.is.http.server.annotation.POST.class),
          getMethod(clazz, br.com.is.http.server.annotation.DELETE.class),
          getMethod(clazz, br.com.is.http.server.annotation.TRACE.class),
          getMethod(clazz, br.com.is.http.server.annotation.PUT.class)
        );
        
        if (!tempDirectory.equals(""))
          ctx.setTempDirectory(tempDirectory);

        if (maxContentLength != null)
          ctx.setMaxContentLength(maxContentLength);

        if (acceptEncode != null)
          ctx.setUseCodeEncoding(acceptEncode);

        contexts.put(urlPattern, ctx);
      }
      
      if (LOGGER.isLoggable(Level.INFO))
        LOGGER.info("Starting the HTTP Server");

      serverChannel = ServerSocketChannel.open();
      serverChannel.socket().setReuseAddress(true);
      serverChannel.socket().bind(addr, backlog);
      
      loop.registerAcceptListener(serverChannel, this);
    }
    catch (IOException | InstantiationException | IllegalAccessException e) {
      if (LOGGER.isLoggable(Level.SEVERE))
        LOGGER.log(Level.SEVERE, "Problems to create a new Server socket", e);

      throw new RuntimeException("Problems to create a new Server socket", e);
    }
    
    loop.run();
  }
    
  /**
   * Stop the running HTTP server.
   * 
   * @param delay Wait for X seconds delay, before force the stop.
   * 
   */
  public void stop(int delay) {
    try {
      loop.stop(delay);
      try {
        serverChannel.close();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
    catch (InterruptedException e) {}
    
    running = false;
  }

  /**
   * Called when the server start a new connection.
   * 
   * @param channel The server socket channel.
   * @param manager Event loop instance used to register the new socket to the event loop.
   * 
   */
  @Override
  public void accept(final ServerSocketChannel channel, final EventLoop manager) {
    SocketChannel socket;
    try {
      socket = channel.accept();
      socket.socket().setTcpNoDelay(true);
      
      if (LOGGER.isLoggable(Level.FINE))
        LOGGER.fine("Accepting a new connection from: " + socket.socket().toString());
    }
    catch (IOException e) {
      if (LOGGER.isLoggable(Level.SEVERE))
        LOGGER.log(Level.SEVERE, "Problems to accept a new HTTP connection", e);

      throw new RuntimeException("Problems to accept a new HTTP connection", e);
    }
    
    try {
      manager.registerReaderListener(socket, new HTTPRequestHandler(new HTTPChannel(socket, createSSLContext(type), manager),
        contexts, sessions, manager));
    }
    catch (Exception e) {
      if (LOGGER.isLoggable(Level.SEVERE))
        LOGGER.log(Level.SEVERE, "Problems to accept a new HTTP connection", e);

      throw new RuntimeException("Problems to accept a new HTTP connection", e);
    }
  }

  /**
   * Add a new HTTP context. The context will be called whenever the server received a request that match the path.
   * 
   * @param path Context Path.
   * @param context HTTP context that will be called whenever a request match to the path.
   * 
   */
  public void addContext(final String path, final HTTPContext context) {
    if (!running)
      contexts.put(path, context);
    else {
      if (LOGGER.isLoggable(Level.SEVERE))
        LOGGER.severe("Cannot add a new request while the server is running");
      
      throw new RuntimeException("Cannot add a new request while the server is running");
    }
  } 
  
  /**
   * Create a new SSL Context.
   * 
   * @param type HTTP connection type (HTTP or HTTPS).
   * 
   * @return The SSL Context or null if it is HTTP.
   * 
   */
  private SSLContext createSSLContext(final Type type) throws Exception {
    if (sslContext == null && type == Type.HTTPS) {
      char[] passphrase = this.passphrase.toCharArray();

      KeyStore ks = KeyStore.getInstance("JKS");
      FileInputStream fis = new FileInputStream(sslCertificate);
      ks.load(fis, passphrase);

      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ks, passphrase);

      TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
      tmf.init(ks);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
      
      fis.close();
      return sslContext;
    }
    
    return sslContext;
  }
  
  private String getClassAnnotationStringValue(final Class<?> clazz, final Class<? extends Annotation> annotationType, final String attributeName) {
    String value = null;

    final Annotation annotation = clazz.getAnnotation(annotationType);
    if (annotation != null) {
      try {
        value = (String) annotation.annotationType().getMethod(attributeName).invoke(annotation);
      } catch (Exception e) {}
    }

    return value;
  }
  
  private Long getClassAnnotationLongValue(final Class<?> clazz, final Class<? extends Annotation> annotationType, final String attributeName) {
    Long value = null;

    final Annotation annotation = clazz.getAnnotation(annotationType);
    if (annotation != null) {
      try {
        value = (Long) annotation.annotationType().getMethod(attributeName).invoke(annotation);
      } catch (Exception e) {}
    }

    return value;
  }
  
  private Boolean getClassAnnotationBooleanValue(final Class<?> clazz, final Class<? extends Annotation> annotationType, final String attributeName) {
    Boolean value = null;

    final Annotation annotation = clazz.getAnnotation(annotationType);
    if (annotation != null) {
      try {
        value = (Boolean) annotation.annotationType().getMethod(attributeName).invoke(annotation);
      } catch (Exception e) {}
    }

    return value;
  }

  private static List<Method> getMethodsAnnotatedWith(final Class<?> clazz, final Class<? extends Annotation> annotationType) {
    final List<Method> methods = new ArrayList<>();

    if (clazz != Object.class) {
      final List<Method> allMethods = new ArrayList<Method>(Arrays.asList(clazz.getDeclaredMethods()));       
      for (final Method method : allMethods) {
        if (annotationType == null || method.isAnnotationPresent(annotationType))
          methods.add(method);
      }
    }
    return methods;
  }
 
  private static Method getMethod(final Class<?> clazz, final Class<? extends Annotation> annotationType) {
    Method method = null;
    final List<Method> getMethods = getMethodsAnnotatedWith(clazz, annotationType);
    if (getMethods.size() > 1) {
      if (LOGGER.isLoggable(Level.SEVERE))
        LOGGER.log(Level.SEVERE, "Problems to create a context. Context has more than 1 "  + annotationType.toString() + " method");

      throw new RuntimeException("Problems to create a context. Context has more than 1 "  + annotationType.toString() + " method");
    }
    else if (getMethods.size() == 1) {
      final java.lang.reflect.Type parameterTypes[] = getMethods.get(0).getGenericParameterTypes();
      if (parameterTypes.length != 2) {
        if (LOGGER.isLoggable(Level.SEVERE))
          LOGGER.log(Level.SEVERE, "Incorrect number of parameters for "  + annotationType.toString() + " method");
        
        throw new RuntimeException("Incorrect number of parameters for "  + annotationType.toString() + " method");
      }
      
      if (parameterTypes[0] != HTTPRequest.class) {
        if (LOGGER.isLoggable(Level.SEVERE))
          LOGGER.log(Level.SEVERE, "First parameter must be HTTPRequest");
        
        throw new RuntimeException("First parameter must be HTTPRequest");
      }
      
      if (parameterTypes[1] != HTTPResponse.class) {
        if (LOGGER.isLoggable(Level.SEVERE))
          LOGGER.log(Level.SEVERE, "First parameter must be HTTPResponse");
        
        throw new RuntimeException("First parameter must be HTTPResponse");
      }
      
      method = getMethods.get(0);
    }
    
    return method;
  } 
}
