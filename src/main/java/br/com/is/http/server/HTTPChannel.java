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
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import br.com.is.nio.EventLoop;

/**
 * This class is responsible to handle all the IN/OUT HTTP/HTTPS communication.
 * 
 * @author Leonardo Bispo de Oliveira
 *
 */
final class HTTPChannel {
  private final SocketChannel channel;
  private final SSLContext    sslContext;
  private final EventLoop     manager;
  private ByteBuffer          remainingData;
  
  private final SSLChannel    sslChannel;
  
  /**
   * Constructor.
   * 
   * @param channel Connection channel.
   * @param sslContext The ssl context if this is an HTTPS channel.
   * @param manager Event loop manager.
   */
  HTTPChannel(final SocketChannel channel, final SSLContext sslContext, final EventLoop manager) {
    this.channel    = channel;
    this.sslContext = sslContext;
    this.manager    = manager;
    if (sslContext != null)
      sslChannel = new SSLChannel(channel, sslContext, manager);
    else
      sslChannel = null;
  }
  
  /**
   * Returns the socket channel.
   * 
   * @return The socket channel.
   * 
   */
  SocketChannel getSocketChannel() {
    return channel;
  }

  /**
   * Execute the SSL handshake. It it is an HTTP connection, it will just ignore it.
   * 
   * @return True if the handshake is done, otherwise false.
   * 
   * @throws IOException, SSLException
   */
  boolean handshake() throws IOException, SSLException {
    if (sslChannel != null)
      return sslChannel.handshake();

    return true;
  }

  /**
   * Read N bytes from the channel and add it to the dst ByteBuffer.
   * 
   * @param dst Buffer that will receive the bytes from the channel.
   * 
   * @return Number of read buffers of -1 if it is end of the connection.
   * 
   * @throws IOException
   * 
   */
  long read(final ByteBuffer dst) throws IOException {
    if (dst == null)
      return 0;
    
    if (remainingData != null)
      return moveRemaining(dst, -1);
    
    if (sslChannel != null)
      return sslChannel.read(dst);
    
    return channel.read(dst);
  }

  /**
   * Read N bytes from the channel and add it to the dst ByteBuffer. If the read datas is bigger than
   * maxLenght, keep it for the next read.
   * 
   * @param dst Buffer that will receive the bytes from the channel.
   * @param maxLength Max number of bytes to be read.
   * 
   * @return Number of read buffers of -1 if it is end of the connection.
   * 
   * @throws IOException
   * 
   */
  long read(final ByteBuffer dst, int maxLength) throws IOException {
    if (dst == null)
      return 0;
    
    if (remainingData != null)
      return moveRemaining(dst, maxLength);
    
    int len = 0;
    if (sslContext != null)
      len = sslChannel.read(dst);
    else
      len = channel.read(dst);
    
    if (len > maxLength) {
      remainingData = ByteBuffer.allocate(dst.limit());
      try {
        remainingData.put(dst.array(), maxLength - 1, dst.limit() - maxLength);
      }
      catch (Exception e) {
        System.out.println("HERE");
      }
      dst.limit(maxLength + 1);

      remainingData.position(maxLength);
      remainingData.compact();
      remainingData.flip();
    }

    return (len < maxLength) ? len : maxLength;
  }
  
  /**
   * Write N bytes to the channel.
   * 
   * @param src Buffer to be written.
   * 
   * @return Effective number of bytes written to the channel
   * 
   * @throws IOException
   * 
   */
  long write(final ByteBuffer buffer) throws IOException {
    if (buffer == null)
      return -1;
    
    if (sslChannel != null)
      return sslChannel.write(buffer);

    return channel.write(buffer);    
  }

  /**
   * Close this channel and unregister it from the event loop manager.
   * 
   * @throws IOException
   * 
   */
  void close() throws IOException {
    if (sslChannel != null)
      sslChannel.shutdown();

    manager.unregisterWriterListener(channel);
    manager.unregisterReaderListener(channel);
    channel.close();
  }
  
  /**
   * Put a remaining read data to the beginning of this channel.
   * 
   * @param buffer Data to be added to this channel.
   * 
   */
  void setRemaining(final ByteBuffer buffer) {
    if (buffer.hasRemaining())
      remainingData = buffer;
  }
  
  /**
   * Return if this is an HTTPS channel.
   * 
   * @return True if it is HTTPS, otherwise false.
   * 
   */
  boolean isSSL() {
    return sslContext != null;
  }
  
  /**
   * Move remaining bytes in this class. This can be happen when the read buffer is not big enough.
   * 
   * @param dst Byte array to receive the remaining data.
   * @param maxLength Max number of bytes to be read.
   * 
   * @return Number of read bytes.
   * 
   */
  private long moveRemaining(final ByteBuffer buffer, int maxLength) {
    if (maxLength == -1) maxLength = buffer.remaining();
    int maxTransfer = Math.min(remainingData.remaining(), maxLength);
    if (maxTransfer > 0) {
      buffer.put(remainingData.array(), 0, maxTransfer);
      remainingData.position(maxTransfer);
      remainingData.compact();
      remainingData.flip();
      if (!remainingData.hasRemaining())
        remainingData = null;
    }
    
    return maxTransfer;
  }
}
