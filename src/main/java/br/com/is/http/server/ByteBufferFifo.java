package br.com.is.http.server;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ByteBufferFifo {
  private static final int BUFFER_SIZE = 4096;
  
  private final AtomicReference<ByteBuffer> currentBuffer = new AtomicReference<>();
  private final BlockingDeque<ByteBuffer> readBuffers  = new LinkedBlockingDeque<>();;
  
  public ByteBufferFifo() {
    currentBuffer.set(ByteBuffer.allocateDirect(BUFFER_SIZE));
  }
  
  public ByteBufferFifo(final ByteBuffer buffer) {
    readBuffers.add(buffer);
  }
  
  public void clear() {
    readBuffers.clear();

    currentBuffer.get().clear();
    currentBuffer.compareAndSet(null, ByteBuffer.allocateDirect(BUFFER_SIZE));
  }
  
  public ByteBuffer getWriteBuffer(boolean force) {
    ByteBuffer buffer = null;
    do {
      buffer = currentBuffer.get();
      if (force || buffer == null || !buffer.hasRemaining()) {
        if (currentBuffer.compareAndSet(buffer, ByteBuffer.allocateDirect(BUFFER_SIZE))) {
          if (buffer != null) {
            buffer.flip();
            readBuffers.add(buffer);
          }
        }

        buffer = null;
      }
    }
    while (buffer == null);

    return buffer;
  }
  
  public ByteBuffer getReadBuffer(boolean force) {
    try {
      ByteBuffer ret = readBuffers.poll(5, TimeUnit.MILLISECONDS);
      
      if (ret == null && force) {
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
}