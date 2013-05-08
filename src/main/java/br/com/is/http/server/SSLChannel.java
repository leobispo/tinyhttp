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

/**
 * Channel that handle SSL Connection. It will be responsible to handle the handshake and all read/write from the ssl socket.
 * 
 * @author Leonardo Bispo de Oliveira
 *
 */
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

  private boolean             shutdown           = false;

  /**
   * Constructor.
   * 
   * @param channel The http socket channel.
   * @param sslContext SSL context that contains information about the keys. 
   * @param manager Event loop manager.
   * 
   */
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

  /**
   * Execute the SSL handshake.
   * 
   * @return True if the handshake is done, otherwise false.
   * 
   * @throws IOException
   */
  boolean handshake() throws IOException, SSLException {    
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

    throw new IllegalStateException("The code reached an impossible state");
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
  int read(final ByteBuffer dst) throws IOException {
    if (!handshakeCompleted)
      return -1;

    if (checkBufRemaining && curBuffer.hasRemaining())
      return moveRemaining(dst, -1);

    if (channel.read(inBuffer) == -1)
        return -1;
    
    if (sslEngine.isInboundDone())
      return -1;
    
    inBuffer.flip();
    SSLEngineResult result = sslEngine.unwrap(inBuffer, curBuffer); 
    inBuffer.compact();
    
    final Status status = result.getStatus();
    if (status == Status.OK) {
      curBuffer.flip();
      return moveRemaining(dst, -1);
    }
    else if (status == Status.BUFFER_OVERFLOW)
      throw new IOException("Buffer is overflow. It should never happens");
    
    return 0;
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
  public long write(final ByteBuffer src) {
    outBuffer.clear();

    SSLEngineResult result = null;
    try {
      result = sslEngine.wrap(src, outBuffer);
    }
    catch (SSLException e) {
      return -1;
    }

    outBuffer.flip();

    switch (result.getStatus()) {
      case OK:
        if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
            executeTask();
        break;
      default:
        throw new IllegalStateException("The read method reached an impossible state: " + result.getStatus().toString());
    }

    if (outBuffer.hasRemaining()) {
      try {
        channel.write(outBuffer);
      }
      catch (IOException e) {
        return -1;
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
  
  /**
   * Shutdown the SSL channel.
   * 
   * @return True if the channel is correclty closed, otherwise false.
   * 
   * @throws IOException
   * 
   */
  boolean shutdown() throws IOException {
    if (!handshakeCompleted)
      return false;
    
    if (!shutdown) {
        sslEngine.closeOutbound();
        shutdown = true;
    }

    if (outBuffer.hasRemaining())
      channel.write(outBuffer);

    outBuffer.clear();
    SSLEngineResult result = sslEngine.wrap(tempBuffer, outBuffer);
    if (result.getStatus() != Status.CLOSED) {
        throw new SSLException("Improper close state");
    }

    outBuffer.flip();
    if (outBuffer.hasRemaining()) {
      if (channel.isOpen()) {
        try {
          channel.write(outBuffer);
        }
        catch (IOException e) {
          outBuffer.clear();
        }
      }
    }

    return (!outBuffer.hasRemaining() && (result.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
  }
  
  /**
   * This method will be called on each time an OP_WRITE event occur.
   * 
   * @param channel Channel that contains the data to be write.
   * @param manager The event loop manager.
   * 
   */
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
    catch (IOException e) {}
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
      else {
        curBuffer.clear();
        checkBufRemaining = false;
      }
    }
    
    return maxTransfer;
  }

  /**
   * Execute the SSL wrap in the Handshake phase.
   * 
   * @throws IOException
   * 
   */
  private void wrapHandshake() throws IOException {
    outBuffer.clear();
    
    final SSLEngineResult result = sslEngine.wrap(tempBuffer, outBuffer);
    outBuffer.flip(); 
    
    final Status status = result.getStatus();

    handshakeStatus = result.getHandshakeStatus();
    if (status != Status.OK)
      throw new IOException("Problems to wrap an ssl message: " + result.getStatus().toString());

    if (handshakeStatus == HandshakeStatus.NEED_TASK)
      handshakeStatus = executeTask();
    
    if (handshakeStatus == HandshakeStatus.FINISHED)
      checkOutRemaining = handshakeCompleted = true;
  }

  /**
   * Execute the SSL unwrap in the Handshake phase.
   * 
   * @throws IOException
   * 
   */
  private boolean unwrapHandshake() throws IOException {
    if (channel.read(inBuffer) == -1) {
      sslEngine.closeInbound();
      throw new SSLException("Problems to read the buffer");
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
            throw new IOException("Handshaking not working");
        }
      }
      else if (status == Status.BUFFER_UNDERFLOW)
        return false;
      else
        throw new IOException("Problems to unwrap an ssl message: " + result.getStatus().toString());
    }
    
    if (handshakeStatus == HandshakeStatus.FINISHED) {
      checkOutRemaining = handshakeCompleted = true;
      return false;
    }
    
    return true;
  }

  /**
   * Execute the SSL task.
   * 
   */
  private HandshakeStatus executeTask() {
    Runnable runnable;

    while ((runnable = sslEngine.getDelegatedTask()) != null)
        runnable.run();
  
    return sslEngine.getHandshakeStatus();
  }
}
