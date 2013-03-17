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
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLContext;

final class HTTPChannel {
  private final SocketChannel channel;
  private final SSLContext    sslContext;
  
  HTTPChannel(final SocketChannel channel, final SSLContext sslContext) {
    this.channel    = channel;
    this.sslContext = sslContext;
  }
  
  SocketChannel getSocketChannel() {
    return channel;
  }

  boolean handshake() throws IOException {
    return true;
  }

  boolean handshake(SelectionKey sk) throws IOException {
    return true;
  }

  long read(final ByteBuffer buffer) throws IOException {
    if (buffer == null)
      return -1;
    
    //TODO: MUST CHECK IF IT IS SSL
    return channel.read(buffer);
  }
  
  boolean shutdown() throws IOException {
    return true;
  }

  void close() throws IOException {
    channel.close();
  }
  
  /*
  int read() throws IOException {
    resizeRequestBB(requestBBSize / 20);
    return channel.read(requestBB);
  }

  ByteBuffer readBuffer() {
    return requestBB;
  }

  int write(ByteBuffer src) throws IOException {
    return channel.write(src);
  }

  long transferTo(FileChannel fc, long pos, long len) throws IOException {
    return fc.transferTo(pos, len, channel);
  }

  boolean dataFlush() throws IOException {
    return true;
  }


 
  private void resizeRequestBB(int remaining) {
    if (requestBB.remaining() < remaining) {
        ByteBuffer bb = ByteBuffer.allocate(requestBB.capacity() * 2);
        requestBB.flip();
        bb.put(requestBB);
        requestBB = bb;
    }
  }
  
  */
}
