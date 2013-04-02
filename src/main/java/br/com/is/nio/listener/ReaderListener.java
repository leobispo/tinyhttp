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
package br.com.is.nio.listener;

import java.nio.channels.SelectableChannel;

import br.com.is.nio.EventLoop;

/**
 * This Interface will be used as a callback for the NIO read events.
 * If you want to register an interest for OP_READ events, register an
 * implemented class inside the EventLoop. Every time that an OP_READ event
 * occurr, the read method will be Called.
 * 
 * @author Leonardo Bispo de Oliveira.
 *
 */
public interface ReaderListener { 
  /**
   * This method will be called for each time that an OP_READ event occurr.
   * 
   * @param channel Channel that contains the data to be read.
   * @param manager The event loop manager.
   * 
   */
  public void read(final SelectableChannel channel, final EventLoop manager);
}
