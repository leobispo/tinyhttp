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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import br.com.is.nio.listener.AcceptListener;
import br.com.is.nio.listener.ReaderListener;
import br.com.is.nio.listener.WriterListener;

public class EventLoop implements Runnable {
  private static final int READ  = 0;
  private static final int WRITE = 1;
  
  private static Object sync = new Object();
  private volatile boolean running = false;
  private Selector selector;

  private final ConcurrentLinkedQueue<Runnable> threads = new ConcurrentLinkedQueue<>();

  private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 10, 20, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(20));

  public EventLoop() { //TODO: SEND THE NUMBER OF ELEMENTS TO THE QUEUE
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

    while (running) {
      try {
        selector.select(); //TODO: Implement the timers!!
        dispatchThreads();
        dispatchSelectionKeys();
      }
      catch(IOException e) {
        throw new RuntimeException("Problems to dispatch the selector", e);
      }
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

  public void registerReaderListener(final SelectableChannel channel, final ReaderListener listener) {
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
        if ((key.interestOps() & SelectionKey.OP_READ) == 0) {
          ((Object[]) key.attachment())[READ] = listener;
          key.interestOps(key.interestOps() | SelectionKey.OP_READ);
          try {
            channel.register(selector, key.interestOps(), key.attachment());
          }
          catch (ClosedChannelException e) {
            //TODO: HANDLE THE EXCEPTION!!
          }
          
          selector.wakeup();
        }
      }
    }
  }
  
  public void registerThreadListener(final Runnable thread) {
    try {
      executor.execute(thread);
    }
    catch (RejectedExecutionException e) {
      threads.add(thread);
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
  
  public void registerWriterListener(final SelectableChannel channel, final WriterListener listener) {
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
        if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
          ((Object[]) key.attachment())[WRITE] = listener;
          key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
          
          try {
            channel.register(selector, key.interestOps(), key.attachment());
          }
          catch (ClosedChannelException e) {
            System.out.println("HERE");
            //TODO: HANDLE THE EXCEPTION!!
          }
          
          selector.wakeup();
        }
      }
    }
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
  
  public void stop() {
    //TODO:WAIT THE EXECUTOR FOR SOMETIME!!
    running = false;
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
}