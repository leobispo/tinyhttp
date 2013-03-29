package br.com.is.nio.listener;

import java.nio.channels.ServerSocketChannel;

import br.com.is.nio.EventLoop;

public interface AcceptListener {
  public void accept(final ServerSocketChannel channel, final EventLoop manager);
}
