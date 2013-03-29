package br.com.is.nio.listener;

import java.nio.channels.SelectableChannel;

import br.com.is.nio.EventLoop;

public interface ReaderListener { 
  public void read(final SelectableChannel channel, final EventLoop manager);
}
