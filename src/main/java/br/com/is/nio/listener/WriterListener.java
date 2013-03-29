package br.com.is.nio.listener;

import java.nio.channels.SelectableChannel;

import br.com.is.nio.EventLoop;

public interface WriterListener {
  public void write(final SelectableChannel channel, final EventLoop manager);
}
