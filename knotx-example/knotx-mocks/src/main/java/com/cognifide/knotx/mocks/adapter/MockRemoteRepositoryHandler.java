/*
 * Knot.x - Mocked services for sample app
 *
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognifide.knotx.mocks.adapter;

import com.google.common.collect.Sets;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import java.util.Set;
import rx.functions.Action0;

public class MockRemoteRepositoryHandler implements Handler<RoutingContext> {

  private static final String SEPARATOR = "/";
  private static final Logger LOGGER = LoggerFactory.getLogger(MockRemoteRepositoryHandler.class);

  private Set<String> textFileExtensions = Sets.newHashSet("html", "php", "html", "js", "css", "txt", "text", "json", "xml", "xsm", "xsl", "xsd",
      "xslt", "dtd", "yml", "svg", "csv", "log", "sgml", "sgm");

  private final Vertx vertx;
  private final String catalogue;
  private long delayAllMs;
  private final JsonObject delayPerPath;

  public MockRemoteRepositoryHandler(Vertx vertx, String catalogue, long delayAllMs, JsonObject delayPerPath) {
    this.vertx = vertx;
    this.catalogue = catalogue;
    this.delayAllMs = delayAllMs;
    this.delayPerPath = delayPerPath;
  }

  @Override
  public void handle(RoutingContext context) {
    String resourcePath = catalogue + SEPARATOR + getContentPath(context.request().path());
    final Optional<String> contentType = Optional.ofNullable(MimeMapping.getMimeTypeForFilename(resourcePath));
    final String fileExtension = getFileExtension(resourcePath);
    final boolean isTextFile = fileExtension != null ? textFileExtensions.contains(fileExtension) : false;

    vertx.fileSystem().readFile(resourcePath, ar -> {
      HttpServerResponse response = context.response();
      if (ar.succeeded()) {
        LOGGER.info("Mocked clientRequest [{}] fetch data from file [{}]", context.request().path(), resourcePath);
        Buffer fileContent = ar.result();
        generateResponse(context.request().path(), () -> {
          setHeaders(response, contentType, isTextFile);
          response.setStatusCode(HttpResponseStatus.OK.code()).end(fileContent);
        });
      } else {
        LOGGER.error("Unable to read file.", ar.cause());
        context.fail(404);
      }
    });
  }

  private long getDelay(String path) {
    if (delayAllMs > 0) {
      return delayAllMs;
    } else {
      long delay = delayPerPath.getJsonObject(path, new JsonObject()).getLong("delayMs", delayAllMs);
      return delay > 0 ? delay : 0L;
    }
  }

  private void generateResponse(String path, Action0 action) {
    long delay = getDelay(path);
    if (delay > 0) {
      LOGGER.info("Delaying response for path {} by {} ms", path, delay);
      vertx.setTimer(delay, timerId -> action.call());
    } else {
      action.call();
    }
  }

  private void setHeaders(HttpServerResponse response, Optional<String> contentType, boolean isTextFile) {
    response.putHeader("Access-Control-Allow-Origin", "*");
    contentType.ifPresent(type -> response.putHeader("Content-Type", createContentType(type, isTextFile)));
    response.putHeader("Server", "Knot.x Repository Mock Server");
    response.putHeader("Cache-control", "no-cache, no-store, must-revalidate");
  }

  private String createContentType(String detectedContentType, boolean isText) {
    if (isText) {
      return detectedContentType + "; charset=UTF-8";
    } else {
      return detectedContentType;
    }
  }

  private String getContentPath(String path) {
    if (path.startsWith("/")) {
      return path.replaceFirst("/", "");
    } else {
      return path;
    }
  }

  public static String getFileExtension(String filename) {
    int li = filename.lastIndexOf('.');
    if (li != -1 && li != filename.length() - 1) {
      return filename.substring(li + 1, filename.length());
    }
    return null;
  }

}
