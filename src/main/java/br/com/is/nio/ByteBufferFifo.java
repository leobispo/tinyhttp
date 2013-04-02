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
package br.com.is.nio;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is store a set of ByteBuffers read from the NIO object.
 * 
 * @author Leonardo Bispo de Oliveira
 *
 */
public final class ByteBufferFifo {
  private static final int BUFFER_SIZE = 4096;
  
  private final AtomicReference<ByteBuffer> currentBuffer = new AtomicReference<>();
  private final BlockingDeque<ByteBuffer> readBuffers     = new LinkedBlockingDeque<>();;
  
  private AtomicBoolean ignoreData = new AtomicBoolean(false);
  
  /**
   * Constructor.
   * 
   */
  public ByteBufferFifo() {
    currentBuffer.set(ByteBuffer.allocateDirect(BUFFER_SIZE));
  }
  
  /**
   * Constructor.
   * 
   * @param buffer If you are implementing a protocol that has a header. Most probably you will have a remaining read buffer.
   * 
   */
  public ByteBufferFifo(final ByteBuffer buffer) {
    readBuffers.add(buffer);
  }
  
  /**
   * Tells to the ByteBufferFifo to flush the buffer and ignore all new write buffer requests.
   * 
   */
  public void stop() {
    if (ignoreData.compareAndSet(false, true)) {
      ignoreData.set(true);
      ByteBuffer buffer = currentBuffer.get();
      if (currentBuffer.compareAndSet(buffer, null) && buffer != null) {
        buffer.flip();
        readBuffers.add(buffer);
      }
    }
  }
  
  /**
   * Request a new BytBuffer to be used to receive new data from the NIO.
   * 
   * @return A ByteBuffer with remaining space or null.
   * 
   */
  public ByteBuffer getWriteBuffer() {
    if (ignoreData.get())
      return null;
    
    ByteBuffer buffer = null;
    do {
      buffer = currentBuffer.get();
      if (buffer == null || !buffer.hasRemaining()) {
        if (currentBuffer.compareAndSet(buffer, ByteBuffer.allocateDirect(BUFFER_SIZE)) && buffer != null) {
          buffer.flip();
          readBuffers.add(buffer);
        }

        buffer = null;
      }
    }
    while (buffer == null);
    
    if (ignoreData.get())
      readBuffers.add(buffer);

    return buffer;
  }
  
  public void invalidateWriteBuffer() {
    if (ignoreData.get())
      return;
    
    ByteBuffer buffer = null;
    do {
      buffer = currentBuffer.get();
      if (currentBuffer.compareAndSet(buffer, ByteBuffer.allocateDirect(BUFFER_SIZE)) && buffer != null) {
        buffer.flip();
        readBuffers.add(buffer);
      }

      buffer = null;
    }
    while (buffer == null);
  }
  
  /**
   * Request a filled buffer. If there is no buffer to read, return null.
   * 
   * @return A filled buffer to be read or null.
   * 
   */
  public ByteBuffer getReadBuffer() {
    try {
      ByteBuffer ret = readBuffers.poll(100, TimeUnit.MILLISECONDS);
      
      if (ret == null) {
        ret = currentBuffer.getAndSet(null);
        if (ret != null)
          ret.flip();
      }
      
      return ret;
    }
    catch (InterruptedException e) {
      return null;
    }    
  }
  
  public void prependByteBuffer(final ByteBuffer buffer) {
    readBuffers.push(buffer);
  }
}