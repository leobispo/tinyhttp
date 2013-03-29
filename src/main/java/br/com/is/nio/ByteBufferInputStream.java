package br.com.is.nio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;

import br.com.is.http.server.ByteBufferFifo;
import br.com.is.nio.listener.ReaderListener;

public class ByteBufferInputStream extends InputStream implements ReaderListener {
  private final SelectableChannel channel;
  
  protected boolean isEof = false;
  
  private final EventLoop      manager;
  private final ByteBufferFifo fifo;
  
  private ByteBuffer currentBuffer = null;
  
  public ByteBufferInputStream(final SelectableChannel channel, final EventLoop manager) {
    this.channel = channel;
    this.manager = manager;
    
    manager.registerReaderListener(channel, this);
    
    fifo = new ByteBufferFifo();
  }
  
  public ByteBufferInputStream(final SelectableChannel channel, final EventLoop manager, final ByteBuffer buffer) {
    this.channel = channel;
    this.manager = manager;
    
    manager.registerReaderListener(channel, this);
    
    fifo = new ByteBufferFifo(buffer);
  }

  @Override
  public int read() throws IOException {
    return -1;
  }

  @Override
  public int read(byte[] dest, int offset, int length) {    
    int readLen = 0;
    while (length > 0) {
      if (currentBuffer == null)
        currentBuffer = fifo.getReadBuffer(isEof);
      
      if (currentBuffer != null) {
        int size = currentBuffer.remaining();
        if (length < currentBuffer.remaining())
          size = length;
        
        currentBuffer.get(dest, offset, size);

        if (!currentBuffer.hasRemaining())
          currentBuffer = null;
        
        offset  += size;
        length  -= size;
        readLen += size;
        
        if (isEof && currentBuffer == null) { //TODO: WHILE I STILL HAVE BUFFER!!
          currentBuffer = fifo.getReadBuffer(isEof);
        }
      }
    }
    
    return (readLen == 0) ? -1 : readLen;
  }
  
  @Override
  public void read(SelectableChannel ch, EventLoop manager) {
    long length = -1;
    do {
      try {
        length = ((ReadableByteChannel) channel).read(fifo.getWriteBuffer(false));
      }
      catch (IOException e) {
        length = -1;
      }
      
      if (length == -1)
        isEof = true;
    } while (length > 0);
  }
}