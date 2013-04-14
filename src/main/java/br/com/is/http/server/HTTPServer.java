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
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

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
  private final EventLoop                              loop          = new EventLoop();
  private final Type                                   type;
  private final File                                   sslCertificate;
  private final String                                 passphrase;
  private final InetSocketAddress                      addr;
  private final int                                    backlog;
  private final Hashtable<String, HTTPContext>         contexts      = new Hashtable<>();
  private boolean                                      running       = false;
  private ServerSocketChannel                          serverChannel = null;
  
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
    this.addr           = addr;
    this.type           = Type.HTTP;
    this.backlog        = backlog;
    this.sslCertificate = null;
    this.passphrase     = null;
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
  public HTTPServer(final InetSocketAddress addr, final int backlog, final File sslCertificate, final String passphrase) throws IOException {
    this.addr    = addr;
    this.type    = Type.HTTPS;
    this.backlog = backlog;
    this.sslCertificate = sslCertificate;
    this.passphrase     = passphrase;
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
      if (LOGGER.isLoggable(Level.INFO))
        LOGGER.info("Starting the HTTP Server");

      serverChannel = ServerSocketChannel.open();
      serverChannel.socket().setReuseAddress(true);
      serverChannel.socket().bind(addr, backlog);
      
      loop.registerAcceptListener(serverChannel, this);
    }
    catch (IOException e) {
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
    for (;;) {
//      try { //TODO: Fix ME!!
        loop.stop();
        try {
          serverChannel.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
//        wait(delay);
        break;
//      }
//      catch (InterruptedException e) {}
    }
    
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
      ks.load(new FileInputStream(sslCertificate), passphrase);

      KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
      kmf.init(ks, passphrase);

      TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
      tmf.init(ks);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
      
      return sslContext;
    }
    
    return sslContext;
  }
}
