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
