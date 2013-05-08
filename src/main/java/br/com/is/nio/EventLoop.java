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
package br.com.is.nio;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import br.com.is.nio.listener.AcceptListener;
import br.com.is.nio.listener.ReaderListener;
import br.com.is.nio.listener.TimerListener;
import br.com.is.nio.listener.WriterListener;

public final class EventLoop implements Runnable {
  private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  
  private static final int READ  = 0;
  private static final int WRITE = 1;
  
  private static Object    sync    = new Object();
  private volatile boolean running = false;
  private Selector         selector;

  private final PriorityBlockingQueue<Timer>    timers  = new PriorityBlockingQueue<>();
  private final ConcurrentLinkedQueue<Runnable> threads = new ConcurrentLinkedQueue<>();

  private final ThreadPoolExecutor executor;

  public EventLoop(int simultaneousConnection) {
    executor = new ThreadPoolExecutor(2, simultaneousConnection, 20, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(20));
    try {
      selector = Selector.open();
    }
    catch (IOException e) {
      throw new RuntimeException("Problems to create a new selector", e);
    }
  }

  public void run() {
    if (running)
      return;
    
    running = true;

    long timeout = 0;
    while (running) {
      try {
        if (timers.isEmpty())
          selector.select(); 
        else {
          timeout = triggerExpiredTimers(System.currentTimeMillis());
          selector.select(timeout);
        }

        dispatchThreads();
        dispatchSelectionKeys();
      }
      catch(IOException e) {
        throw new RuntimeException("Problems to dispatch the selector", e);
      }
    }

    synchronized(sync) {
      sync.notifyAll();
    }

    try {
      selector.close();
    }
    catch (IOException e) {
      throw new RuntimeException("Problems to close the selector", e);
    }
    finally {
      running  = false;
      try {
        selector = Selector.open();
      }
      catch (IOException e) {
        throw new RuntimeException("Problems to create a new selector", e);
      }
    }
  }
  
  public void registerAcceptListener(final ServerSocketChannel channel, final AcceptListener listener) {
    synchronized (sync) {
      if (channel.keyFor(selector) == null) {
        try {
          channel.configureBlocking(false);
        
          selector.wakeup();
          channel.register(selector, SelectionKey.OP_ACCEPT, listener); 
        }
        catch (IOException e) {
          throw new RuntimeException("Problems to register a selector", e);
        }
      }
    }
  }
  
  public boolean unregisterAcceptListener(final ServerSocketChannel channel) {
    synchronized (sync) {
      SelectionKey key = channel.keyFor(selector);
      if (key != null && (key.interestOps() & SelectionKey.OP_ACCEPT) != 0) {
        key.cancel();
        return true;
      }
 
      return false;
    }
  }

  public ReaderListener registerReaderListener(final SelectableChannel channel, final ReaderListener listener) {
    ReaderListener oldListener = null;
    synchronized (sync) {
      SelectionKey key = channel.keyFor(selector);
      if (key == null || !key.isValid()) {
        try {
          channel.configureBlocking(false);

          selector.wakeup();

          Object listeners[] = new Object[2];
          listeners[READ]  = listener;
          listeners[WRITE] = null;
          channel.register(selector, SelectionKey.OP_READ, listeners); 
        }
        catch (IOException e) {
          throw new RuntimeException("Problems to register a selector", e);
        }
      }
      else {
        if ((key.interestOps() & SelectionKey.OP_READ) != 0)
          oldListener = ((ReaderListener)((Object[]) key.attachment())[READ]);
        
        ((Object[]) key.attachment())[READ] = listener;
        key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        try {
          channel.register(selector, key.interestOps(), key.attachment());
        }
        catch (ClosedChannelException e) {
          if (LOGGER.isLoggable(Level.WARNING))
            LOGGER.log(Level.WARNING, "Channel unexpectedly closed", e);
        }

        selector.wakeup();
      }
      
      return oldListener;
    }
  }
  
  public void registerThreadListener(final Runnable thread) {
    try {
      executor.execute(thread);
    }
    catch (RejectedExecutionException e) {
      threads.add(thread);
      selector.wakeup();
    }
  }

  public boolean unregisterReaderListener(final SelectableChannel channel) {
    synchronized (sync) {
      SelectionKey key = channel.keyFor(selector);
      if (key != null && key.isValid() && (key.interestOps() & SelectionKey.OP_READ) != 0) {
        ((Object[]) key.attachment())[READ] = null;

        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        return true;
      }

      return false;
    }
  }
  
  public WriterListener registerWriterListener(final SelectableChannel channel, final WriterListener listener) {
    WriterListener oldListener = null;
    synchronized (sync) {
      SelectionKey key = channel.keyFor(selector);
      if (key == null || !key.isValid()) {
        try {
          channel.configureBlocking(false);

          selector.wakeup();

          Object listeners[] = new Object[2];
          listeners[WRITE] = listener;
          listeners[READ]  = null;
          channel.register(selector, SelectionKey.OP_WRITE, listeners); 
        }
        catch (IOException e) {
          throw new RuntimeException("Problems to register a selector", e);
        }
      }
      else {
        if ((key.interestOps() & SelectionKey.OP_WRITE) != 0)
          oldListener = ((WriterListener)((Object[]) key.attachment())[WRITE]);

        ((Object[]) key.attachment())[WRITE] = listener;
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

        try {
          channel.register(selector, key.interestOps(), key.attachment());
        }
        catch (ClosedChannelException e) {
          if (LOGGER.isLoggable(Level.WARNING))
            LOGGER.log(Level.WARNING, "Channel unexpectedly closed", e);
        }

        selector.wakeup();
      }
    }
    
    return oldListener;
  }

  public boolean unregisterWriterListener(final SelectableChannel channel) {
    synchronized (sync) {
      SelectionKey key = channel.keyFor(selector);
      if (key != null && key.isValid() && (key.interestOps() & SelectionKey.OP_WRITE) != 0) {
        ((Object[]) key.attachment())[WRITE] = null;

        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        return true;
      }

      return false;
    }
  }
  
  public void registerTimer(int msecs, final TimerListener listener) {
    if (msecs < 0)
      throw new IllegalArgumentException("Cannot have milliseconds in the past");
    
    if (listener == null)
      throw new IllegalArgumentException("Cannot have invalid handler");

    long expireMS = System.currentTimeMillis() + msecs;
    timers.add(new Timer(expireMS, listener));
    selector.wakeup();
  }
  
  public void updateTimer(int msecs, final TimerListener listener) {
    cancelTimer(listener);
    registerTimer(msecs, listener);
  }
  
  public void cancelTimer(final TimerListener listener) {
    Iterator<Timer> timerIterator = timers.iterator();
    while (timerIterator.hasNext()) {
      Timer timer = timerIterator.next();
      if (timer.handler == listener) {
        timerIterator.remove();
        return;
      }
    }
  }
  
  public void stop(int delay) throws InterruptedException {
    synchronized(sync) {
      running = false;
      selector.wakeup();
      executor.shutdownNow();

      sync.wait(delay);
    }
  }

  private long triggerExpiredTimers(long now) {
    while (!timers.isEmpty()) {
      Timer trigger = timers.peek();
      if (trigger.expireMS <= now) {
        timers.poll();

        trigger.handler.timeout();
      } else {
        long timeoutMs = trigger.expireMS - now;
        return timeoutMs;
      }
    }

    return 0;
  }
  
  private void dispatchThreads() {
    Runnable thread = null;
    while (running && (thread = threads.poll()) != null) {
      try {
        executor.execute(thread);
      }
      catch (RejectedExecutionException e) {
        break;
      }
    }
  }

  private void dispatchSelectionKeys() {
    Set<SelectionKey> keys;
    synchronized (sync) {
      keys = selector.selectedKeys();
    }

    for (Iterator<SelectionKey> it = keys.iterator(); it.hasNext();) {
      final SelectionKey key = it.next();

      it.remove();

      if (key.isValid() && key.isReadable() && ((Object[]) key.attachment())[READ] != null)
        ((ReaderListener)((Object[]) key.attachment())[READ]).read(key.channel(), this);
      
      if (key.isValid() && key.isWritable() && ((Object[]) key.attachment())[WRITE] != null)
        ((WriterListener)((Object[]) key.attachment())[WRITE]).write(key.channel(), this);
      
      if (key.isValid() && key.isAcceptable())
        ((AcceptListener) key.attachment()).accept((ServerSocketChannel) key.channel(), this);
    }
  }
  
  private static final class Timer implements Comparable<Timer> {
    public final long expireMS;
    public final TimerListener handler;

    public Timer(long expireMS, TimerListener handler) {
      this.expireMS = expireMS;
      this.handler      = handler;
    }

    @Override
    public int compareTo(Timer other) {
      if (this.expireMS < other.expireMS) return -1;
      if (this.expireMS > other.expireMS) return 1;
      return 0;
    }

    @Override
    public boolean equals(Object other) {
      throw new RuntimeException("TODO: implement");
    }

    @Override
    public int hashCode() {
      throw new RuntimeException("TODO: implement");
    }
  }
}
