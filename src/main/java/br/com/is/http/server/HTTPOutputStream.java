package br.com.is.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;

import br.com.is.nio.ByteBufferFifo;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.WriterListener;

final class HTTPOutputStream extends OutputStream implements WriterListener {
  private final EventLoop manager;
  private final HTTPChannel channel;

  private final ByteBufferFifo fifo = new ByteBufferFifo();

  public HTTPOutputStream(final HTTPChannel channel, final EventLoop manager) {
    this.channel = channel;
    this.manager = manager;

    manager.registerWriterListener(channel.getSocketChannel(), this);
  }

  @Override
  public void write(int b) throws IOException {
    for (;;) {
      ByteBuffer next = fifo.getWriteBuffer();
      try {
        next.putInt(b);
        break;
      }
      catch (BufferOverflowException e) {
        next = fifo.getWriteBuffer(); // TODO: Not enough space.... must invalidate the old one!!
      }
    }
  }

  @Override                                                              
  public void write(byte[] source, int offset, int length) {             
    while (length > 0) {
      ByteBuffer next = fifo.getWriteBuffer();

      int bytesToWrite = length;
      if (next.remaining() < bytesToWrite) bytesToWrite = next.remaining();
      next.put(source, offset, bytesToWrite);
      offset += bytesToWrite;
      length -= bytesToWrite;
    }
  }

  @Override
  public void flush() {
    write(channel.getSocketChannel(), manager);
  }

  @Override
  public void write(final SelectableChannel ch, final EventLoop manager) {
    ByteBuffer buffer;
    while ((buffer = fifo.getReadBuffer()) != null) {
      try {
        channel.write(buffer);
      }
      catch (IOException e) {
        throw new RuntimeException(e); //TODO: Shouldn't throw an exception... Just Ignore LOG and ignore
      }

      if (buffer.remaining() != 0) {
        manager.registerWriterListener(channel.getSocketChannel(), this);
        break;
      }
      else
        manager.unregisterWriterListener(channel.getSocketChannel());
    }
  }
  
  public void sendError(final String message, int error) {
    
  }
  
}