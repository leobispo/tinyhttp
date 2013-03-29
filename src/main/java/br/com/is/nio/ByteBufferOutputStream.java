package br.com.is.nio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;

import br.com.is.http.server.ByteBufferFifo;
import br.com.is.nio.listener.WriterListener;


public class ByteBufferOutputStream extends OutputStream implements WriterListener {
  private final EventLoop manager;
  private final SelectableChannel channel;
  
  private final ByteBufferFifo fifo = new ByteBufferFifo();

  public ByteBufferOutputStream(final SelectableChannel channel, final EventLoop manager) {
    this.channel = channel;
    this.manager = manager;
    
    manager.registerWriterListener(channel, this);
  }

  @Override
  public void write(int b) throws IOException {
    boolean force = false;
    
    for (;;) {
      ByteBuffer next = fifo.getWriteBuffer(force);
      try {
        next.putInt(b);
        break;
      }
      catch (BufferOverflowException e) {
        next = fifo.getWriteBuffer(true);
        force = true;
      }
    }
  }

  @Override                                                              
  public void write(byte[] source, int offset, int length) {             
    while (length > 0) {
      ByteBuffer next = fifo.getWriteBuffer(false);

      int bytesToWrite = length;
      if (next.remaining() < bytesToWrite) bytesToWrite = next.remaining();
      next.put(source, offset, bytesToWrite);
      offset += bytesToWrite;
      length -= bytesToWrite;
    }
  }

  @Override
  public void flush() {
    write(channel, manager);
  }

  @Override
  public void write(final SelectableChannel channel, final EventLoop manager) {
    ByteBuffer buffer;
    while ((buffer = fifo.getReadBuffer(false)) != null) {
      try {
        ((WritableByteChannel)channel).write(buffer);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }

      if (buffer.remaining() != 0) {
        manager.registerWriterListener(channel, this);
        break;
      }
      else
        manager.unregisterWriterListener(channel);
    }
  }
}