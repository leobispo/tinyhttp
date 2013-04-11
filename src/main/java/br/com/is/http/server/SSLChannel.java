package br.com.is.http.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.WriterListener;

class SSLChannel implements WriterListener {
  private static final ByteBuffer tempBuffer = ByteBuffer.allocate(0);
  
  private final EventLoop     manager;
  private final SSLEngine     sslEngine;
  private final SocketChannel channel;

  private ByteBuffer buffer;
  private ByteBuffer inBuffer;
  private ByteBuffer outBuffer;

  private HandshakeStatus handshakeStatus    = HandshakeStatus.NEED_UNWRAP;
  private boolean         handshakeCompleted = false;
  private boolean         checkOutRemaining  = false;
  private boolean         checkBufRemaining  = false;

  protected SSLChannel(final SocketChannel channel, final SSLContext sslContext, final EventLoop manager) {
    this.channel = channel;

    this.manager = manager;
    sslEngine    = sslContext.createSSLEngine();
    sslEngine.setUseClientMode(false);

    int pbSize = sslEngine.getSession().getPacketBufferSize();
    
    inBuffer  = ByteBuffer.allocate(pbSize);
    outBuffer = ByteBuffer.allocate(pbSize);
    buffer    = ByteBuffer.allocate(pbSize);
    
    outBuffer.position(0);
    outBuffer.limit(0);
    
    manager.registerWriterListener(channel, this);
  }

  boolean handshake() throws IOException {    
    if (handshakeCompleted) {
      if (checkOutRemaining) {
        if (outBuffer.hasRemaining())
          return false;

        //buffer.compact();
        manager.unregisterWriterListener(channel);
        checkOutRemaining = false;
      }
      return true;
    }

    switch (handshakeStatus) {
      case NEED_UNWRAP:
        if (!unwrapHandshake())
          return handshakeCompleted;
      case NEED_WRAP:
        wrapHandshake();
        return handshakeCompleted;
      default:
      break;
    }

    //TODO: Throw an exception. It should never be here!
    return true;
  }

  int read(final ByteBuffer dst) throws IOException {
    if (!handshakeCompleted)
      return -1;

    if (checkBufRemaining && buffer.hasRemaining())
      return moveRemaining(dst, -1);

    if (channel.read(inBuffer) == -1)
        return -1;

    SSLEngineResult result;
    do {
      inBuffer.flip();
      result = sslEngine.unwrap(inBuffer, buffer); 
      inBuffer.compact();
      
      final Status status = result.getStatus();
      if (status == Status.OK && result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
          executeTask();
      else if (status == Status.BUFFER_UNDERFLOW) {
        int pbSize = sslEngine.getSession().getPacketBufferSize();
        if (pbSize > inBuffer.capacity()) {
          ByteBuffer newBuffer = ByteBuffer.allocate(pbSize);
          inBuffer.flip();
          newBuffer.put(inBuffer);
          inBuffer = newBuffer;
        }
      }
      else if (status == Status.BUFFER_OVERFLOW) {
        int pbSize = sslEngine.getSession().getPacketBufferSize();
        if (buffer.remaining() < pbSize) {
          ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
          buffer.flip();
          newBuffer.put(buffer);
          buffer = newBuffer;
        }
      }
      else {
        //TODO: Handle the error here!!
      }     
    } while ((inBuffer.position() != 0) && result.getStatus() != Status.BUFFER_UNDERFLOW);
    
    if (result.getStatus() != Status.BUFFER_UNDERFLOW)
      buffer.flip();

    return moveRemaining(dst, -1);
  }

  @Override
  public void write(SelectableChannel ch, EventLoop manager) {
    try {
      if (outBuffer.hasRemaining())
        channel.write(outBuffer); 
      else
        handshake();
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  private int moveRemaining(final ByteBuffer dst, int maxLength) {
    if (maxLength == -1) maxLength = dst.remaining();
    int maxTransfer = Math.min(buffer.remaining(), maxLength);
    if (maxTransfer > 0) {
      dst.put(buffer.array(), 0, maxTransfer);
      buffer.position(maxTransfer);
      buffer.compact();
      buffer.flip();
      if (buffer.hasRemaining())
        checkBufRemaining = true;
      else
        checkBufRemaining = false;
    }
    
    return maxTransfer;
  }

  private void wrapHandshake() throws IOException {
    outBuffer.clear();
    
    final SSLEngineResult result = sslEngine.wrap(tempBuffer, outBuffer);
    outBuffer.flip(); 
    
    final Status status = result.getStatus();

    handshakeStatus = result.getHandshakeStatus();
    if (status != Status.OK) {
      //TODO: THROW AN EXCEPTION!
    }

    if (handshakeStatus == HandshakeStatus.NEED_TASK)
      handshakeStatus = executeTask();
    
    if (handshakeStatus == HandshakeStatus.FINISHED)
      checkOutRemaining = handshakeCompleted = true;
  }

  private boolean unwrapHandshake() throws IOException {
    if (channel.read(inBuffer) == -1) {
      sslEngine.closeInbound();
      return false;
    }
    
    while (handshakeStatus == HandshakeStatus.NEED_UNWRAP) {
      inBuffer.flip();
      final SSLEngineResult result = sslEngine.unwrap(inBuffer, buffer);
      inBuffer.compact();
      
      final Status status = result.getStatus();
      
      handshakeStatus = result.getHandshakeStatus();
      if (status == Status.OK) {
        switch (handshakeStatus) {
          case FINISHED:
            checkOutRemaining = handshakeCompleted = true;
            return false;
          case NEED_TASK:
            handshakeStatus = executeTask();
          break;
          case NEED_WRAP:
          case NEED_UNWRAP:
            continue;
          case NOT_HANDSHAKING:
            //TODO: Implement ME!! - Send an exception, so I can handle it after
          break;
        }
      }
      else if (status == Status.BUFFER_UNDERFLOW) {
        int pbSize = sslEngine.getSession().getPacketBufferSize();
        if (pbSize > inBuffer.capacity()) {
          ByteBuffer newBuffer = ByteBuffer.allocate(pbSize);
          inBuffer.flip();
          newBuffer.put(inBuffer);
          inBuffer = newBuffer;
        }

        return false;
      }
      else if (status == Status.BUFFER_OVERFLOW) {
        int pbSize = sslEngine.getSession().getPacketBufferSize();
        if (buffer.remaining() < pbSize) {
          ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
          buffer.flip();
          newBuffer.put(buffer);
          buffer = newBuffer;
        }
      }
      else {
        //TODO: Implement ME!! - Send an exception, so I can handle it after
      }
    }
    
    if (handshakeStatus == HandshakeStatus.FINISHED) {
      checkOutRemaining = handshakeCompleted = true;
      return false;
    }
    
    return true;
  }

  private HandshakeStatus executeTask() {
    Runnable runnable;

    while ((runnable = sslEngine.getDelegatedTask()) != null)
        runnable.run();
    
    
    return sslEngine.getHandshakeStatus();
  }
}
