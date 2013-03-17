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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.Hashtable;
import java.util.Iterator;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Provides a simple high-level asynchronous Http server API, which can be used to build embedded HTTP servers.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
public final class HTTPServer implements Runnable {
  enum Type { HTTP, HTTPS }
  
  private final Type                           type;
  private final InetSocketAddress              addr;
  private final int                            backlog;
  private final Selector                       selector;
  private final Hashtable<String, HTTPContext> contexts = new Hashtable<>();
  private final Object                         gate     = new Object();
  private boolean                              running  = false;
  
  /**
   * Constructor.
   * 
   * @param addr Address to listen on.
   * @param backlog The socket backlog. If this value is less than or equal to zero, then a system default value is used.
   * @param type HTTP connection type (HTTP or HTTPS).
   * 
   * @throws IOException
   * 
   */
  public HTTPServer(final InetSocketAddress addr, int backlog, Type type) throws IOException {
    this.addr    = addr;
    this.type    = type;
    this.backlog = backlog;
    selector     = Selector.open();
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
    final HTTPConnectionHandler connHandler = new HTTPConnectionHandler(this, createSSLContext(type));
    connHandler.start();
    
    running = true;
    while (running) {
      try {
        selector.select();
        for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
          final SelectionKey key = it.next();
          it.remove();
          ((HTTPRequestHandler) key.attachment()).handle(key);
        }
        synchronized (gate) {}
      }
      catch(IOException ioe) {
        throw new RuntimeException("Problems to dispatch the selector", ioe);
      }
    }
    
    try {
      connHandler.end();
      connHandler.wait();
    }
    catch (IOException | InterruptedException e) {
      throw new RuntimeException("Problems to finish the Connection Handler", e);
    }
  }
    
  /**
   * Stop the running HTTP server.
   * 
   * @param delay Wait for X seconds delay, before force the stop.
   * 
   */
  public void stop(int delay) {
    try {
      wait(delay);
    }
    catch (InterruptedException e) {
      //TODO: Implement ME!!
    }
    
    running = false;
  }

  /**
   * Add a new HTTP contex. The context will be called whenever the server received a request that match the path.
   * 
   * @param path Context Path.
   * @param context HTTP context that will be called whenever a request match to the path.
   * 
   */
  public void addContext(final String path, final HTTPContext context) {
    if (!running)
      contexts.put(path, context);
    else
      throw new RuntimeException("Cannot add a new request while the server is running");
  }  
  
  /**
   * Helper method to create a new HTTP Request handler and add it to the AIO.
   * 
   * @param channel Selectable channel used by AIO to do asynchronous Read/Write.
   * @param ops The interest set for the resulting key.
   * @param handler The handler for the resulting key
   * 
   * @throws IOException
   * 
   */
  private void registerNewHandler(final SelectableChannel channel, int ops, final HTTPRequestHandler handler) throws IOException {
    synchronized (gate) {
      selector.wakeup();
      channel.register(selector, ops, handler);
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
  private SSLContext createSSLContext(final Type type) {
    if (type == Type.HTTPS) {
      try {
        final char[] passphrase = "passphrase".toCharArray();

        final KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("testkeys"), passphrase);

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
      }
      catch (Exception e) {
        throw new RuntimeException("Problem to create a new SSL Context", e);
      }
    }
    
    return null;
  }
  
  /**
   * Class responsible for handling all the HTTP/TCP connection accept.
   * 
   * @author Leonardo Bispo de Oliveira.
   *
   */
  private class HTTPConnectionHandler extends Thread {
    private boolean                 exit          = false;
    private ServerSocketChannel     serverChannel = null;
    
    private final HTTPServer        server;
    private final SSLContext        sslContext;
    
    /**
     * Constructor.
     * 
     * @param server Parent's instance.
     * @param sslContext The SSL context if the connection is HTTPS, otherwise null.
     * 
     */
    public HTTPConnectionHandler(final HTTPServer server, final SSLContext sslContext) {
      this.sslContext = sslContext;
      this.server     = server;
    }
    
    /**
     * This is the thread's run override method and will be used to accept new connections and register them
     * to the AIO selector.
     * 
     */
    @Override
    public void run() {
      try {
        serverChannel = ServerSocketChannel.open();
        serverChannel.socket().setReuseAddress(true);
        serverChannel.socket().bind(server.addr, server.backlog);
      }
      catch (IOException ioe) {
        throw new RuntimeException("Problems to start a new Socket", ioe);
      }

      while (!exit) {
        try {
          SocketChannel channel = serverChannel.accept();
          channel.configureBlocking(false);
          
          if (channel.isOpen() && !exit) {
            HTTPRequestHandler handler = new HTTPRequestHandler(new HTTPChannel(channel, sslContext), server.contexts);
            server.registerNewHandler(channel, SelectionKey.OP_READ, handler);
          }
        }
        catch (IOException ioe) {
          throw new RuntimeException("Problems to accept data from the Socket", ioe);
        }
      }
    }
    
    /**
     * Stop this thread, causing the HTTP service to be stopped as well.
     * 
     * @throws IOException
     * 
     */
    public void end() throws IOException {
      exit = true;
      if (serverChannel != null)
        serverChannel.close();
    }
  }
  
  //FIXME: Only For tests!! - Remove this when writing unit tests!!
  public static void main(String... args) throws InterruptedException, IOException {
    System.out.println("Starting HTTP Class");
    HTTPServer server = new HTTPServer(new InetSocketAddress(9999), 10, HTTPServer.Type.HTTP);
    server.run();
    while (true) {
      Thread.sleep(1000);
    }
  }
}
