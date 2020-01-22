/**
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby;

import com.typesafe.config.Config;
import io.jooby.exception.MissingValueException;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Provider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * Routing DSL functions.
 *
 * @since 2.0.0
 * @author edgar
 */
public interface Router extends Registry {

  /**
   * Find route result.
   */
  interface Match {
    /**
     * True for matching route.
     *
     * @return True for matching route.
     */
    boolean matches();

    /**
     * Matched route.
     *
     * @return Matched route.
     */
    @Nonnull Route route();

    void execute(@Nonnull Context context);

    /**
     * Path pattern variables.
     *
     * @return Path pattern variables.
     */
    @Nonnull Map<String, String> pathMap();
  }

  /** HTTP GET. */
  String GET = "GET";
  /** HTTP POST. */
  String POST = "POST";
  /** HTTP PUT. */
  String PUT = "PUT";
  /** HTTP DELETE. */
  String DELETE = "DELETE";
  /** HTTP PATCH. */
  String PATCH = "PATCH";
  /** HTTP HEAD. */
  String HEAD = "HEAD";
  /** HTTP OPTIONS. */
  String OPTIONS = "OPTIONS";
  /** HTTP TRACE. */
  String TRACE = "TRACE";

  /** HTTP Methods. */
  List<String> METHODS = unmodifiableList(
      asList(GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE));

  /** Web socket. */
  String WS = "WS";

  /** Sever-Sent events. */
  String SSE = "SSE";

  /**
   * Application configuration.
   *
   * @return Application configuration.
   */
  @Nonnull Config getConfig();

  /**
   * Mutable map of application attributes.
   *
   * @return Mutable map of application attributes.
   */
  @Nonnull Map<String, Object> getAttributes();

  /**
   * Get an attribute by his key. This is just an utility method around {@link #getAttributes()}.
   *
   * @param key Attribute key.
   * @param <T> Attribute type.
   * @return Attribute value.
   */
  @Nonnull default <T> T attribute(@Nonnull String key) {
    T attribute = (T) getAttributes().get(key);
    if (attribute == null) {
      throw new MissingValueException(key);
    }
    return attribute;
  }

  /**
   * Set an application attribute.
   *
   * @param key Attribute key.
   * @param value Attribute value.
   * @return This router.
   */
  @Nonnull default Router attribute(@Nonnull String key, Object value) {
    getAttributes().put(key, value);
    return this;
  }

  /**
   * Application service registry. Services are accessible via this registry or
   * {@link Jooby#require(Class)} calls.
   *
   * This method returns a mutable registry. You are free to modify/alter the registry.
   *
   * @return Service registry.
   */
  @Nonnull ServiceRegistry getServices();

  /**
   * Set application context path. Context path is the base path for all routes. Default is:
   * <code>/</code>.
   *
   * @param contextPath Context path.
   * @return This router.
   */
  @Nonnull Router setContextPath(@Nonnull String contextPath);

  /**
   * Get application context path (a.k.a as base path).
   *
   * @return Application context path (a.k.a as base path).
   */
  @Nonnull String getContextPath();

  /* ***********************************************************************************************
   * use(Router)
   * ***********************************************************************************************
   */

  /**
   * Import routes from given router. Predicate works like a filter and only when predicate pass
   * the routes match against the current request.
   *
   * Example of domain predicate filter:
   *
   * <pre>{@code
   * {
   *
   *   use(ctx -> ctx.getHost().equals("foo.com"), new FooApp());
   *   use(ctx -> ctx.getHost().equals("bar.com"), new BarApp());
   * }
   * }</pre>
   *
   * Imported routes are matched only when predicate pass.
   *
   * @param predicate Context predicate.
   * @param router Router to import.
   * @return This router.
   */
  @Nonnull Router use(@Nonnull Predicate<Context> predicate, @Nonnull Router router);

  /**
   * Import all routes from the given router and prefix them with the given path.
   *
   * @param path Prefix path.
   * @param router Router to import.
   * @return This router.
   */
  @Nonnull Router use(@Nonnull String path, @Nonnull Router router);

  /**
   * Import all routes from the given router.
   *
   * @param router Router to import.
   * @return This router.
   */
  @Nonnull Router use(@Nonnull Router router);

  /* ***********************************************************************************************
   * Mvc
   * ***********************************************************************************************
   */

  /**
   * Import all route method from the given controller class. At runtime the controller instance
   * is resolved by calling {@link Jooby#require(Class)}.
   *
   * @param router Controller class.
   * @return This router.
   */
  @Nonnull Router mvc(@Nonnull Class router);

  /**
   * Import all route method from the given controller class.
   *
   * @param router Controller class.
   * @param provider Controller provider.
   * @param <T> Controller type.
   * @return This router.
   */
  @Nonnull <T> Router mvc(@Nonnull Class<T> router, @Nonnull Provider<T> provider);

  /**
   * Import all route methods from given controller instance.
   *
   * @param router Controller instance.
   * @return This routes.
   */
  @Nonnull Router mvc(@Nonnull Object router);

  /**
   * Add a websocket handler.
   *
   * @param pattern WebSocket path pattern.
   * @param handler WebSocket handler.
   * @return A new route.
   */
  @Nonnull Route ws(@Nonnull String pattern, @Nonnull WebSocket.Initializer handler);

  /**
   * Add a server-sent event handler.
   *
   * @param pattern Path pattern.
   * @param handler Handler.
   * @return A new route.
   */
  @Nonnull Route sse(@Nonnull String pattern, @Nonnull ServerSentEmitter.Handler handler);

  /**
   * Returns all routes.
   *
   * @return All routes.
   */
  @Nonnull List<Route> getRoutes();

  /**
   * Register a route response encoder.
   *
   * @param encoder MessageEncoder instance.
   * @return This router.
   */
  @Nonnull Router encoder(@Nonnull MessageEncoder encoder);

  /**
   * Register a route response encoder.
   *
   * @param contentType Accept header should matches the content-type.
   * @param encoder MessageEncoder instance.
   * @return This router.
   */
  @Nonnull Router encoder(@Nonnull MediaType contentType, @Nonnull MessageEncoder encoder);

  /**
   * Application temporary directory.
   *
   * @return Application temporary directory.
   */
  @Nonnull Path getTmpdir();

  /**
   * Register a decoder for the given content type.
   *
   * @param contentType Content type to match.
   * @param decoder MessageDecoder.
   * @return This router.
   */
  @Nonnull Router decoder(@Nonnull MediaType contentType, @Nonnull MessageDecoder decoder);

  /**
   * Returns the worker thread pool. This thread pool is used to run application blocking code.
   *
   * @return Worker thread pool.
   */
  @Nonnull Executor getWorker();

  /**
   * Set a worker thread pool. This thread pool is used to run application blocking code.
   *
   * @param worker Worker thread pool.
   * @return This router.
   */
  @Nonnull Router setWorker(@Nonnull Executor worker);

  /**
   * Set the default worker thread pool. Via this method the underlying web server set/suggests the
   * worker thread pool that should be used it.
   *
   * A call to {@link #getWorker()} returns the default thread pool, unless you explicitly set one.
   *
   * @param worker Default worker thread pool.
   * @return This router.
   */
  @Nonnull Router setDefaultWorker(@Nonnull Executor worker);

  /**
   * Add a route decorator to the route pipeline.
   *
   * @param decorator Decorator.
   * @return This router.
   */
  @Nonnull Router decorator(@Nonnull Route.Decorator decorator);

  /**
   * Add a before route decorator to the route pipeline.
   *
   * @param before Before decorator.
   * @return This router.
   */
  @Nonnull Router before(@Nonnull Route.Before before);

  /**
   * Add an after route decorator to the route pipeline.
   *
   * @param after After decorator.
   * @return This router.
   */
  @Nonnull Router after(@Nonnull Route.After after);

  /**
   * Dispatch route pipeline to the {@link #getWorker()} worker thread pool. After dispatch
   * application code is allowed to do blocking calls.
   *
   * @param body Dispatch body.
   * @return This router.
   */
  @Nonnull Router dispatch(@Nonnull Runnable body);

  /**
   * Dispatch route pipeline to the given executor. After dispatch application code is allowed to
   * do blocking calls.
   *
   * @param executor Executor. {@link java.util.concurrent.ExecutorService} instances automatically
   *    shutdown at application exit.
   * @param body Dispatch body.
   * @return This router.
   */
  @Nonnull Router dispatch(@Nonnull Executor executor, @Nonnull Runnable body);

  /**
   * Group one or more routes. Useful for applying cross cutting concerns to the enclosed routes.
   *
   * @param body Route body.
   * @return This router.
   */
  @Nonnull Router route(@Nonnull Runnable body);

  /**
   * Group one or more routes under a common path prefix. Useful for applying cross cutting
   * concerns to the enclosed routes.
   *
   * @param pattern Path pattern.
   * @param body Route body.
   * @return This router.
   */
  @Nonnull Router path(@Nonnull String pattern, @Nonnull Runnable body);

  /**
   * Add a HTTP GET handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route get(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(GET, pattern, handler);
  }

  /**
   * Add a HTTP POST handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route post(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(POST, pattern, handler);
  }

  /**
   * Add a HTTP PUT handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route put(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(PUT, pattern, handler);
  }

  /**
   * Add a HTTP DELETE handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route delete(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(DELETE, pattern, handler);
  }

  /**
   * Add a HTTP PATCH handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route patch(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(PATCH, pattern, handler);
  }

  /**
   * Add a HTTP HEAD handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route head(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(HEAD, pattern, handler);
  }

  /**
   * Add a HTTP OPTIONS handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route options(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(OPTIONS, pattern, handler);
  }

  /**
   * Add a HTTP TRACE handler.
   *
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull default Route trace(@Nonnull String pattern, @Nonnull Route.Handler handler) {
    return route(TRACE, pattern, handler);
  }

  /**
   * Add a static resource handler. Static resources are resolved from file system.
   *
   * @param pattern Path pattern.
   * @param source File system directory.
   * @return A route.
   */
  default @Nonnull Route assets(@Nonnull String pattern, @Nonnull Path source) {
    return assets(pattern, AssetSource.create(source));
  }

  /**
   * Add a static resource handler. Static resources are resolved from:
   *
   * - file-system if the source folder exists in the current user directory
   * - or fallback to classpath when file-system folder doesn't exist.
   *
   * @param pattern Path pattern.
   * @param source File-System folder when exists, or fallback to a classpath folder.
   * @return A route.
   */
  default @Nonnull Route assets(@Nonnull String pattern, @Nonnull String source) {
    Path path = Stream.of(source.split("/"))
        .reduce(Paths.get(System.getProperty("user.dir")), Path::resolve, Path::resolve);
    if (Files.exists(path)) {
      return assets(pattern, path);
    }
    return assets(pattern, AssetSource.create(getClass().getClassLoader(), source));
  }

  /**
   * Add a static resource handler. Static resources are resolved from root classpath.
   *
   * @param pattern Path pattern.
   * @return A route.
   */
  default @Nonnull Route assets(@Nonnull String pattern) {
    return assets(pattern, AssetSource.create(getClass().getClassLoader(), "/"));
  }

  /**
   * Add a static resource handler.
   *
   * @param pattern Path pattern.
   * @param source Asset sources.
   * @return A route.
   */
  default @Nonnull Route assets(@Nonnull String pattern, @Nonnull AssetSource... source) {
    return assets(pattern, new AssetHandler(source));
  }

  /**
   * Add a static resource handler.
   *
   * @param pattern Path pattern.
   * @param handler Asset handler.
   * @return A route.
   */
  default @Nonnull Route assets(@Nonnull String pattern, @Nonnull AssetHandler handler) {
    return route(GET, pattern, handler);
  }

  /**
   * Add a route.
   *
   * @param method HTTP method.
   * @param pattern Path pattern.
   * @param handler Application code.
   * @return A route.
   */
  @Nonnull Route route(@Nonnull String method, @Nonnull String pattern, @Nonnull
      Route.Handler handler);

  /**
   * Find a matching route using the given context.
   *
   * If no match exists this method returns a route with a <code>404</code> handler.
   * See {@link Route#NOT_FOUND}.
   *
   * @param ctx Web Context.
   * @return A route match result.
   */
  @Nonnull Match match(@Nonnull Context ctx);

  /**
   * Find a matching route using the given context.
   *
   * If no match exists this method returns a route with a <code>404</code> handler.
   * See {@link Route#NOT_FOUND}.
   *
   * @param method Method to match.
   * @param path Path to match.
   * @return A route match result.
   */
  boolean match(@Nonnull String method, @Nonnull String path);

  /* Error handler: */

  /**
   * Map an exception type to a status code.
   *
   * @param type Exception type.
   * @param statusCode Status code.
   * @return This router.
   */
  @Nonnull Router errorCode(@Nonnull Class<? extends Throwable> type,
      @Nonnull StatusCode statusCode);

  /**
   * Computes the status code for the given exception.
   *
   * @param cause Exception.
   * @return Status code.
   */
  @Nonnull StatusCode errorCode(@Nonnull Throwable cause);

  /**
   * Add a custom error handler that matches the given status code.
   *
   * @param statusCode Status code.
   * @param handler Error handler.
   * @return This router.
   */
  @Nonnull
  default Router error(@Nonnull StatusCode statusCode, @Nonnull ErrorHandler handler) {
    return error(statusCode::equals, handler);
  }

  /**
   * Add a custom error handler that matches the given exception type.
   *
   * @param type Exception type.
   * @param handler Error handler.
   * @return This router.
   */
  @Nonnull
  default Router error(@Nonnull Class<? extends Throwable> type,
      @Nonnull ErrorHandler handler) {
    return error((ctx, x, statusCode) -> {
      if (type.isInstance(x) || type.isInstance(x.getCause())) {
        handler.apply(ctx, x, statusCode);
      }
    });
  }

  /**
   * Add a custom error handler that matches the given predicate.
   *
   * @param predicate Status code filter.
   * @param handler Error handler.
   * @return This router.
   */
  @Nonnull
  default Router error(@Nonnull Predicate<StatusCode> predicate,
      @Nonnull ErrorHandler handler) {
    return error((ctx, x, statusCode) -> {
      if (predicate.test(statusCode)) {
        handler.apply(ctx, x, statusCode);
      }
    });
  }

  /**
   * Add a custom error handler.
   *
   * @param handler Error handler.
   * @return This router.
   */
  @Nonnull Router error(@Nonnull ErrorHandler handler);

  /**
   * Get the error handler.
   *
   * @return An error handler.
   */
  @Nonnull ErrorHandler getErrorHandler();

  /**
   * Application logger.
   *
   * @return Application logger.
   */
  @Nonnull Logger getLog();

  /**
   * Add a response handler factory.
   *
   * @param factory Response handler factory.
   * @return This router.
   */
  @Nonnull Router responseHandler(@Nonnull ResponseHandler factory);

  /**
   * Router options.
   *
   * @return Router options.
   */
  @Nonnull Set<RouterOption> getRouterOptions();

  /**
   * Set router options.
   *
   * @param options router options.
   * @return This router.
   */
  @Nonnull Router setRouterOptions(@Nonnull RouterOption... options);

  /**
   * Session store. Default use a cookie ID with a memory storage.
   *
   * See {@link SessionStore#memory()}.
   *
   * @return Session store.
   */
  @Nonnull SessionStore getSessionStore();

  /**
   * Set session store.
   *
   * @param store Session store.
   * @return This router.
   */
  @Nonnull Router setSessionStore(@Nonnull SessionStore store);

  /**
   * Get an executor from application registry.
   *
   * @param name Executor name.
   * @return Executor.
   */
  default @Nonnull Executor executor(@Nonnull String name) {
    return require(Executor.class, name);
  }

  /**
   * Put an executor into the application registry.
   *
   * @param name Executor's name.
   * @param executor Executor.
   * @return This router.
   */
  @Nonnull Router executor(@Nonnull String name, @Nonnull Executor executor);

  /**
   * Name of the flash cookie. Defaults is: <code>jooby.flash</code>.
   *
   * @return Name of the flash cookie. Defaults is: <code>jooby.flash</code>.
   */
  @Nonnull String getFlashCookie();

  /**
   * Set flash cookie name.
   *
   * @param name Flash cookie name.
   * @return This router.
   */
  @Nonnull Router setFlashCookie(@Nonnull String name);

  /**
   * Add a custom string value converter.
   *
   * @param converter Custom value converter.
   * @return This router.
   */
  @Nonnull Router converter(@Nonnull ValueConverter converter);

  /**
   * Add a custom bean value converter.
   *
   * @param converter Custom value converter.
   * @return This router.
   */
  @Nonnull Router converter(@Nonnull BeanConverter converter);

  /**
   * Get all simple/string value converters.
   *
   * @return All simple/string value converters.
   */
  @Nonnull List<ValueConverter> getConverters();

  /**
   * Get all complex/bean value converters.
   *
   * @return All complex/bean value converters.
   */
  @Nonnull List<BeanConverter> getBeanConverters();

  /**
   * Available server options.
   *
   * @return Server options.
   */
  @Nonnull ServerOptions getServerOptions();

  /**
   * Ensure path start with a <code>/</code>(leading slash).
   *
   * @param path Path to process.
   * @return Path with leading slash.
   */
  static @Nonnull String leadingSlash(@Nullable String path) {
    if (path == null || path.length() == 0 || path.equals("/")) {
      return "/";
    }
    return path.charAt(0) == '/' ? path : "/" + path;
  }

  /**
   * Strip trailing slashes.
   *
   * @param path Path to process.
   * @return Path without trailing slashes.
   */
  static @Nonnull String noTrailingSlash(@Nonnull String path) {
    StringBuilder buff = new StringBuilder(path);
    int i = buff.length() - 1;
    while (i > 0 && buff.charAt(i) == '/') {
      buff.setLength(i);
      i -= 1;
    }
    if (path.length() != buff.length()) {
      return buff.toString();
    }
    return path;
  }

  /**
   * Normalize a path by removing consecutive <code>/</code>(slashes).
   *
   * @param path Path to process.
   * @return Safe path pattern.
   */
  static @Nonnull String normalizePath(@Nullable String path) {
    if (path == null || path.length() == 0 || path.equals("/")) {
      return "/";
    }
    int len = path.length();
    boolean modified = false;
    int p = 0;
    char[] buff = new char[len + 1];
    if (path.charAt(0) != '/') {
      buff[p++] = '/';
      modified = true;
    }
    for (int i = 0; i < path.length(); i++) {
      char ch = path.charAt(i);
      if (ch != '/') {
        buff[p++] = ch;
      } else if (i == 0 || path.charAt(i - 1) != '/') {
        buff[p++] = ch;
      } else {
        // double slash
        modified = true;
      }
    }
    // creates string?
    return modified ? new String(buff, 0, p) : path;
  }

  /**
   * Extract path keys from given path pattern. A path key (a.k.a path variable) looks like:
   *
   * <pre>/product/{id}</pre>
   *
   * @param pattern Path pattern.
   * @return Path keys.
   */
  static @Nonnull List<String> pathKeys(@Nonnull String pattern) {
    List<String> result = new ArrayList<>();
    int start = -1;
    int end = Integer.MAX_VALUE;
    int len = pattern.length();
    int curly = 0;
    for (int i = 0; i < len; i++) {
      char ch = pattern.charAt(i);
      if (ch == '{') {
        if (curly == 0) {
          start = i + 1;
          end = Integer.MAX_VALUE;
        }
        curly += 1;
      } else if (ch == ':') {
        end = i;
      } else if (ch == '}') {
        curly -= 1;
        if (curly == 0) {
          String id = pattern.substring(start, Math.min(i, end));
          result.add(id);
          start = -1;
          end = Integer.MAX_VALUE;
        }
      } else if (ch == '*') {
        if (i == len - 1) {
          result.add("*");
        } else {
          result.add(pattern.substring(i + 1));
        }
        i = len;
      }
    }
    switch (result.size()) {
      case 0:
        return Collections.emptyList();
      case 1:
        return Collections.singletonList(result.get(0));
      default:
        return unmodifiableList(result);
    }
  }

  /**
   * Recreate a path pattern using the given variables. Variable replacement is done using the
   * current index.
   *
   * @param pattern Path pattern.
   * @param values Path keys.
   * @return Path.
   */
  static @Nonnull String reverse(@Nonnull String pattern, @Nonnull Object... values) {
    Map<String, Object> keys = new HashMap<>();
    IntStream.range(0, values.length).forEach(k -> keys.put(Integer.toString(k), values[k]));
    return reverse(pattern, keys);
  }

  /**
   * Recreate a path pattern using the given variables.
   *
   * @param pattern Path pattern.
   * @param keys Path keys.
   * @return Path.
   */
  static @Nonnull String reverse(@Nonnull String pattern, @Nonnull Map<String, Object> keys) {
    StringBuilder path = new StringBuilder();
    int start = 0;
    int end = Integer.MAX_VALUE;
    int len = pattern.length();
    int keyIdx = 0;
    for (int i = 0; i < len; i++) {
      char ch = pattern.charAt(i);
      if (ch == '{') {
        path.append(pattern, start, i);
        start = i + 1;
        end = Integer.MAX_VALUE;
      } else if (ch == ':') {
        end = i;
      } else if (ch == '}') {
        String id = pattern.substring(start, Math.min(i, end));
        Object value = keys.getOrDefault(id, keys.get(Integer.toString(keyIdx++)));
        requireNonNull(value, "Missing key: '" + id + "'");
        path.append(value);
        start = i + 1;
        end = Integer.MAX_VALUE;
      } else if (ch == '*') {
        path.append(pattern, start, i);
        String id;
        if (i == len - 1) {
          id = "*";
        } else {
          id = pattern.substring(i + 1);
        }
        Object value = keys.getOrDefault(id, keys.get(Integer.toString(keyIdx++)));
        requireNonNull(value, "Missing key: '" + id + "'");
        path.append(value);
        start = len;
        i = len;
      }
    }
    if (path.length() == 0) {
      return pattern;
    }
    if (start > 0) {
      path.append(pattern, start, len);
    }
    return path.toString();
  }
}
