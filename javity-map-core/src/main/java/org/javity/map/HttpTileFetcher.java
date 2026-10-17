package org.javity.map;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Objects;

/** Configured HTTP XYZ provider. No implicit public OSM endpoint. Owns HttpClient. */
public final class HttpTileFetcher implements TileFetcher {
  private final HttpClient client;
  private final String template, userAgent, attribution;
  private volatile boolean closed;

  public HttpTileFetcher(String template, String userAgent, String attribution) {
    this.template = Objects.requireNonNull(template);
    this.userAgent = Objects.requireNonNull(userAgent);
    this.attribution = Objects.requireNonNull(attribution);
    if (!template.contains("{z}")
        || !template.contains("{x}")
        || !template.contains("{y}")
        || userAgent.isBlank())
      throw new IllegalArgumentException("XYZ template and User-Agent required");
    client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  public String attribution() {
    return attribution;
  }

  public byte[] fetch(TileKey key) throws IOException, InterruptedException {
    if (closed) throw new IllegalStateException("HTTP source closed");
    URI uri =
        URI.create(
            template
                .replace("{z}", "" + key.zoom())
                .replace("{x}", "" + key.x())
                .replace("{y}", "" + key.y()));
    if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))
      throw new IllegalArgumentException("HTTP(S) required");
    var request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", userAgent)
            .GET()
            .build();
    var future = client.sendAsync(request, info -> new LimitedBody());
    try {
      var response = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
      if (response.statusCode() == 404 || response.statusCode() == 410) return null;
      if (response.statusCode() != 200)
        throw new IOException("Tile HTTP status " + response.statusCode());
      byte[] bytes = response.body();
      if (bytes.length == 0) throw new IOException("Empty tile response");
      return bytes;
    } catch (java.util.concurrent.TimeoutException e) {
      future.cancel(true);
      throw new IOException("Tile body timeout", e);
    } catch (java.util.concurrent.ExecutionException e) {
      throw new IOException("Tile request failed", e.getCause());
    } catch (InterruptedException e) {
      future.cancel(true);
      throw e;
    }
  }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final java.util.concurrent.CompletableFuture<byte[]> result =
        new java.util.concurrent.CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private java.util.concurrent.Flow.Subscription subscription;

    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return result;
    }

    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if (buffer.remaining() > TileByteCache.MAX_BYTES - bytes.size()) {
          subscription.cancel();
          result.completeExceptionally(new IOException("Tile response exceeds size limit"));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    public void onError(Throwable error) {
      result.completeExceptionally(error);
    }

    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }

  public void close() {
    closed = true;
    client.shutdownNow();
  }
}
