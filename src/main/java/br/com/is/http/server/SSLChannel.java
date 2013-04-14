package br.com.is.http.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Semaphore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.WriterListener;

class SSLChannel implements WriterListener {
  private static final ByteBuffer tempBuffer = ByteBuffer.allocate(0);
  
  private final EventLoop     manager;
  private final SSLEngine     sslEngine;
  private final SocketChannel channel;

  private ByteBuffer curBuffer;
  private ByteBuffer inBuffer;
  private ByteBuffer outBuffer;

  private HandshakeStatus     handshakeStatus    = HandshakeStatus.NEED_UNWRAP;
  private boolean             handshakeCompleted = false;
  private boolean             checkOutRemaining  = false;
  private boolean             checkBufRemaining  = false;
  
  private volatile boolean    isWriting          = false;
  private WriterListener      oldListener        = null;
  private final Semaphore     sem                = new Semaphore(0);

  protected SSLChannel(final SocketChannel channel, final SSLContext sslContext, final EventLoop manager) {
    this.channel = channel;

    this.manager = manager;
    sslEngine    = sslContext.createSSLEngine();
    sslEngine.setUseClientMode(false);

    int pbSize = sslEngine.getSession().getPacketBufferSize();
    
    inBuffer  = ByteBuffer.allocate(pbSize);
    outBuffer = ByteBuffer.allocate(pbSize);
    curBuffer = ByteBuffer.allocate(pbSize);
    
    outBuffer.position(0);
    outBuffer.limit(0);
    
    manager.registerWriterListener(channel, this);
  }

  boolean handshake() throws IOException {    
    if (handshakeCompleted) {
      if (checkOutRemaining) {
        if (outBuffer.hasRemaining())
          return false;

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

    if (checkBufRemaining && curBuffer.hasRemaining())
      return moveRemaining(dst, -1);

    if (channel.read(inBuffer) == -1)
        return -1;

    SSLEngineResult result;
    do {
      inBuffer.flip();
      result = sslEngine.unwrap(inBuffer, curBuffer); 
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
        if (curBuffer.remaining() < pbSize) {
          ByteBuffer newBuffer = ByteBuffer.allocate(curBuffer.capacity() * 2);
          curBuffer.flip();
          newBuffer.put(curBuffer);
          curBuffer = newBuffer;
        }
      }
      else {
        //TODO: Handle the error here!!
      }     
    } while ((inBuffer.position() != 0) && result.getStatus() != Status.BUFFER_UNDERFLOW);
    
    if (result.getStatus() != Status.BUFFER_UNDERFLOW)
      curBuffer.flip();

    return moveRemaining(dst, -1);
  }

  public long write(final ByteBuffer buffer) {
    outBuffer.clear();

    SSLEngineResult result = null;
    try {
      result = sslEngine.wrap(buffer, outBuffer);
    }
    catch (SSLException e) {
      return -1; //TODO: Handle the exception
    }

    outBuffer.flip();

    switch (result.getStatus()) {
      case OK:
        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
            executeTask();
        break;
      default:
        //TODO: Handle the exception
    }

    if (outBuffer.hasRemaining()) {
      try {
        channel.write(outBuffer);
      }
      catch (IOException e) {
        //TODO: Handle the exception
      }
    }

    if (outBuffer.hasRemaining()) {
      isWriting = true;
      oldListener = manager.registerWriterListener(channel, this);
      try {
        sem.acquire();
      }
      catch (InterruptedException e) {}
      manager.registerWriterListener(channel, oldListener);
    }

    return result.bytesConsumed();
  }
  
  @Override
  public void write(SelectableChannel ch, EventLoop manager) {
    try {
      if (outBuffer.hasRemaining())
        channel.write(outBuffer); 
      else
        handshake();
      
      if (!outBuffer.hasRemaining() && isWriting) {
        isWriting = false;
        sem.release();
      }
    }
    catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  private int moveRemaining(final ByteBuffer dst, int maxLength) {
    if (maxLength == -1) maxLength = dst.remaining();
    int maxTransfer = Math.min(curBuffer.remaining(), maxLength);
    if (maxTransfer > 0) {
      dst.put(curBuffer.array(), 0, maxTransfer);
      curBuffer.position(maxTransfer);
      curBuffer.compact();
      curBuffer.flip();
      if (curBuffer.hasRemaining())
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
      final SSLEngineResult result = sslEngine.unwrap(inBuffer, curBuffer);
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
        if (curBuffer.remaining() < pbSize) {
          ByteBuffer newBuffer = ByteBuffer.allocate(curBuffer.capacity() * 2);
          curBuffer.flip();
          newBuffer.put(curBuffer);
          curBuffer = newBuffer;
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
