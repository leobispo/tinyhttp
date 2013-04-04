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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import br.com.is.nio.ByteBufferFifo;
import br.com.is.nio.EventLoop;
import br.com.is.nio.listener.ReaderListener;

/**
 * Implementation of InputStream for the HTTP NIO classes.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
final class HTTPInputStream extends InputStream implements ReaderListener {
  private long                 availableRead;
  private final HTTPChannel    channel;

  private final ByteBufferFifo fifo = new ByteBufferFifo();

  protected AtomicBoolean      isEof  = new AtomicBoolean(false);
  private ByteBuffer           buffer = null;

  /**
   * Constructor.
   * 
   * @param channel HTTP Channel that handles both, HTTP or HTTPS connections.
   * @param manager Event loop instance.
   * 
   */
  public HTTPInputStream(final HTTPChannel channel, final EventLoop manager) {
    this(channel, manager, 0);
  }

  /**
   * Constructor.
   * 
   * @param channel HTTP Channel that handles both, HTTP or HTTPS connections.
   * @param manager Event loop instance.
   * @param contentLength How many bytes should I read, before considering EoS.
   * 
   */
  public HTTPInputStream(final HTTPChannel channel, final EventLoop manager, long contentLength) {
    this.channel       = channel;
    this.availableRead = contentLength;

    read(channel.getSocketChannel(), manager);    
    manager.registerReaderListener(channel.getSocketChannel(), this);  
  }

  /**
   * Reads the next byte of data from the input stream. The value byte is
   * returned as an <code>int</code> in the range <code>0</code> to
   * <code>255</code>. If no byte is available because the end of the stream
   * has been reached, the value <code>-1</code> is returned. This method
   * blocks until input data is available, the end of the stream is detected,
   * or an exception is thrown.
   *
   * @return     the next byte of data, or <code>-1</code> if the end of the
   *             stream is reached.
   * @exception  IOException  if an I/O error occurs.
   *
   */
  @Override
  public int read() throws IOException {
    throw new RuntimeException("Method is not implemented. Should never been called.");
  }

  /**
   * Reads up to <code>len</code> bytes of data from the input stream into
   * an array of bytes.  An attempt is made to read as many as
   * <code>len</code> bytes, but a smaller number may be read.
   * The number of bytes actually read is returned as an integer.
   *
   * <p> This method blocks until input data is available, end of file is
   * detected, or an exception is thrown.
   *
   * <p> If <code>len</code> is zero, then no bytes are read and
   * <code>0</code> is returned; otherwise, there is an attempt to read at
   * least one byte. If no byte is available because the stream is at end of
   * file, the value <code>-1</code> is returned; otherwise, at least one
   * byte is read and stored into <code>b</code>.
   *
   * <p> The first byte read is stored into element <code>b[off]</code>, the
   * next one into <code>b[off+1]</code>, and so on. The number of bytes read
   * is, at most, equal to <code>len</code>. Let <i>k</i> be the number of
   * bytes actually read; these bytes will be stored in elements
   * <code>b[off]</code> through <code>b[off+</code><i>k</i><code>-1]</code>,
   * leaving elements <code>b[off+</code><i>k</i><code>]</code> through
   * <code>b[off+len-1]</code> unaffected.
   *
   * <p> In every case, elements <code>b[0]</code> through
   * <code>b[off]</code> and elements <code>b[off+len]</code> through
   * <code>b[b.length-1]</code> are unaffected.
   *
   * <p> The <code>read(b,</code> <code>off,</code> <code>len)</code> method
   * for class <code>InputStream</code> simply calls the method
   * <code>read()</code> repeatedly. If the first such call results in an
   * <code>IOException</code>, that exception is returned from the call to
   * the <code>read(b,</code> <code>off,</code> <code>len)</code> method.  If
   * any subsequent call to <code>read()</code> results in a
   * <code>IOException</code>, the exception is caught and treated as if it
   * were end of file; the bytes read up to that point are stored into
   * <code>b</code> and the number of bytes read before the exception
   * occurred is returned. The default implementation of this method blocks
   * until the requested amount of input data <code>len</code> has been read,
   * end of file is detected, or an exception is thrown. Subclasses are encouraged
   * to provide a more efficient implementation of this method.
   *
   * @param      b     the buffer into which the data is read.
   * @param      off   the start offset in array <code>b</code>
   *                   at which the data is written.
   * @param      len   the maximum number of bytes to read.
   * 
   * @return     the total number of bytes read into the buffer, or
   *             <code>-1</code> if there is no more data because the end of
   *             the stream has been reached.
   *             
   * @exception  IOException If the first byte cannot be read for any reason
   * other than end of file, or if the input stream has been closed, or if
   * some other I/O error occurs.
   * @exception  NullPointerException If <code>b</code> is <code>null</code>.
   * @exception  IndexOutOfBoundsException If <code>off</code> is negative,
   * <code>len</code> is negative, or <code>len</code> is greater than
   * <code>b.length - off</code>
   * 
   * @see        java.io.InputStream#read()
   */
  @Override
  public int read(byte[] dest, int offset, int length) {
    int readLen = 0;

    do {
      while (buffer != null) {
        int size = buffer.remaining();

        if (length < buffer.remaining())
          size = length;

        buffer.get(dest, offset, size);
        if (!buffer.hasRemaining())
          buffer = null;

        offset        += size;
        length        -= size;
        readLen       += size;

        if (length == 0)
          return readLen;
      }

      buffer = fifo.getReadBuffer();
    } while (buffer != null);

    return (readLen == 0) ? -1 : readLen;
  }

  /**
   * This method will be called for each time that an OP_READ event occurr.
   * 
   * @param channel Channel that contains the data to be read.
   * @param manager The event loop manager.
   * 
   */
  @Override
  public void read(SelectableChannel ch, EventLoop manager) {
    long length = -1;
    do {
      try {
        length = channel.read(fifo.getWriteBuffer(), availableRead > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) availableRead);
        availableRead -= length;
      }
      catch (IOException e) {
        length = -1;
      }
    } while (length > 0 && availableRead > 0);
    
    if (length == -1 || availableRead <= 0) {
      fifo.stop();
      isEof.set(true);
    }
  }
}
