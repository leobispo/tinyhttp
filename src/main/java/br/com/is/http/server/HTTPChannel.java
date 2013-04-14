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

import br.com.is.nio.EventLoop;

final class HTTPChannel {
  private final SocketChannel channel;
  private final SSLContext    sslContext;
  private final EventLoop     manager;
  private ByteBuffer          remainingData;
  
  private final SSLChannel    sslChannel;
  
  HTTPChannel(final SocketChannel channel, final SSLContext sslContext, final EventLoop manager) {
    this.channel    = channel;
    this.sslContext = sslContext;
    this.manager    = manager;
    if (sslContext != null)
      sslChannel = new SSLChannel(channel, sslContext, manager);
    else
      sslChannel = null;
  }
  
  SocketChannel getSocketChannel() {
    return channel;
  }

  boolean handshake() {
    if (sslChannel != null) {
      try {
        return sslChannel.handshake();
      }
      catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    return true;
  }

  long read(final ByteBuffer buffer) throws IOException {
    if (buffer == null)
      return 0;
    
    if (remainingData != null)
      return moveRemaining(buffer, -1);
    
    if (sslChannel != null)
      return sslChannel.read(buffer);
    
    return channel.read(buffer);
  }

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

  long read(final ByteBuffer buffer, int maxLength) throws IOException {
    if (buffer == null)
      return 0;
    
    if (remainingData != null)
      return moveRemaining(buffer, maxLength);
    
    int len = 0;
    if (sslContext != null)
      len = sslChannel.read(buffer);
    else
      len = channel.read(buffer);
    
    if (len > maxLength) {
      remainingData = ByteBuffer.allocate(buffer.limit());
      try {
        remainingData.put(buffer.array(), maxLength - 1, buffer.limit() - maxLength);
      }
      catch (Exception e) {
        System.out.println("HERE");
      }
      buffer.limit(maxLength + 1);

      remainingData.position(maxLength);
      remainingData.compact();
      remainingData.flip();
    }

    return (len < maxLength) ? len : maxLength;
  }
  
  long write(final ByteBuffer buffer) throws IOException {
    if (buffer == null)
      return -1;
    
    if (sslChannel != null)
      return sslChannel.write(buffer);

    return channel.write(buffer);    
  }

  void close() throws IOException {
//    if (sslChannel != null && sslChannel.isFinishedHandshake())
//      sslChannel.shutdown();
    manager.unregisterWriterListener(channel);
    manager.unregisterReaderListener(channel);
    channel.close();
  }
  
  void setRemaining(final ByteBuffer buffer) {
    if (buffer.hasRemaining())
      remainingData = buffer;
  }
  
  boolean isSSL() {
    return sslContext != null;
  }
}
