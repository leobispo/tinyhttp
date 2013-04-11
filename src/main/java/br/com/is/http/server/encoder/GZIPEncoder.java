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
package br.com.is.http.server.encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * Implementation of GZIP encoding method.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
public class GZIPEncoder implements Encoder {
  private GZIPOutputStream            gos;
  private final ByteArrayOutputStream os        = new ByteArrayOutputStream();
  private boolean                     isFlushed = true;
  
  /**
   * Compress the bytes sent to this method and keep it on memory until the flush method be called.
   * 
   * @param source Bytes to be Compressed.
   * @param offset Initial offset to be compressed.
   * @param length Length of source to be compressed.
   * 
   */
  @Override
  public void compress(byte[] source, int offset, int length) throws IOException {
    if (gos == null)
      gos = new GZIPOutputStream(os, true);

    isFlushed = false;
    gos.write(source, offset, length);
  }

  /**
   * Flush the data and return the compressed information.
   * 
   * @return Compressed data.
   * 
   */
  @Override
  public byte[] flush() throws IOException {
    if (isFlushed || gos == null)
      return null;

    isFlushed = true;
    gos.flush();
    os.flush();

    byte b[] = os.toByteArray();
    os.reset();
    return b;
  }
  
  /**
   * Return the compression method name.
   * 
   * @return Compression Method Name.
   * 
   */
  @Override
  public String getType() {
    return "gzip";
  }
}
