/**
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby.netty;

import io.jooby.Jooby;
import io.jooby.Server;
import io.jooby.ServerOptions;
import io.jooby.SneakyThrows;
import io.jooby.internal.netty.NettyNative;
import io.jooby.internal.netty.NettyPipeline;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

import javax.annotation.Nonnull;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Web server implementation using <a href="https://netty.io/">Netty</a>.
 *
 * @author edgar
 * @since 2.0.0
 */
public class Netty extends Server.Base {

  static {
    System.setProperty("io.netty.leakDetection.level",
        System.getProperty("io.netty.leakDetection.level", "disabled"));
  }

  private static final int BACKLOG = 8192;

  private List<Jooby> applications = new ArrayList<>();

  private EventLoopGroup acceptorloop;

  private EventLoopGroup eventloop;

  private ExecutorService worker;

  private ServerOptions options = new ServerOptions()
      .setServer("netty");

  @Override public Netty setOptions(@Nonnull ServerOptions options) {
    this.options = options;
    return this;
  }

  @Nonnull @Override public ServerOptions getOptions() {
    return options;
  }

  @Nonnull @Override public Server start(@Nonnull Jooby application) {
    try {
      applications.add(application);

      addShutdownHook();

      /** Worker: Application blocking code */
      worker = Executors.newFixedThreadPool(
          options.getWorkerThreads(),
          new DefaultThreadFactory("worker")
      );
      fireStart(applications, worker);

      /** Disk attributes: */
      String tmpdir = applications.get(0).getTmpdir().toString();
      DiskFileUpload.baseDirectory = tmpdir;
      DiskAttribute.baseDirectory = tmpdir;

      NettyNative provider = NettyNative.get(getClass().getClassLoader());

      /** Acceptor event-loop */
      this.acceptorloop = provider.group("acceptor", 1);

      /** Event loop: processing connections, parsing messages and doing engine's internal work */
      this.eventloop = provider.group("eventloop", options.getIoThreads());

      /** File data factory: */
      HttpDataFactory factory = new DefaultHttpDataFactory(options.getBufferSize());

      /** Bootstrap: */
      ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap.option(ChannelOption.SO_BACKLOG, BACKLOG);
      bootstrap.option(ChannelOption.SO_REUSEADDR, true);

      bootstrap.group(acceptorloop, eventloop)
          .channel(provider.channel())
          .childHandler(new NettyPipeline(acceptorloop.next(),
              applications.get(0),
              factory,
              options.getDefaultHeaders(),
              options.getGzip(),
              options.getBufferSize(),
              options.getMaxRequestSize()))
          .childOption(ChannelOption.SO_REUSEADDR, true)
          .childOption(ChannelOption.TCP_NODELAY, true);

      bootstrap.bind("0.0.0.0", options.getPort()).get();

      fireReady(applications);
    } catch (InterruptedException x) {
      throw SneakyThrows.propagate(x);
    } catch (ExecutionException x) {
      Throwable cause = x.getCause();
      if (cause instanceof BindException) {
        cause = new BindException("Address already in use: " + options.getPort());
      }
      throw SneakyThrows.propagate(cause);
    }
    return this;
  }

  @Nonnull @Override public synchronized Server stop() {
    fireStop(applications);
    if (acceptorloop != null) {
      acceptorloop.shutdownGracefully();
      acceptorloop = null;
    }
    if (eventloop != null) {
      eventloop.shutdownGracefully();
      eventloop = null;
    }
    if (worker != null) {
      worker.shutdown();
      worker = null;
    }
    return this;
  }
}