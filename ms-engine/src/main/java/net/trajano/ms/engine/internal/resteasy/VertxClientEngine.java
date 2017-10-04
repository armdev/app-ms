package net.trajano.ms.engine.internal.resteasy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import net.trajano.ms.engine.internal.Conversions;
import net.trajano.ms.engine.internal.VertxOutputStream;

public class VertxClientEngine implements
    ClientHttpEngine {

    private final HttpClient httpClient;

    private final SSLContext sslContext;

    public VertxClientEngine(final HttpClient httpClient) {

        this.httpClient = httpClient;
        try {
            sslContext = SSLContext.getDefault();
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {

        System.out.println("closing");
        httpClient.close();

    }

    @Override
    public HostnameVerifier getHostnameVerifier() {

        return null;
    }

    @Override
    public SSLContext getSslContext() {

        return sslContext;
    }

    @Override
    public ClientResponse invoke(final ClientInvocation request) {

        final RequestOptions options = Conversions.toRequestOptions(request.getUri());
        final HttpClientRequest httpClientRequest = httpClient.request(HttpMethod.valueOf(request.getMethod()), options);

        final VertxClientResponse clientResponse = new VertxClientResponse(request.getClientConfiguration(), httpClientRequest);

        request.getHeaders().asMap().forEach((name,
            value) -> {
            httpClientRequest.putHeader(name, value);
        });

        try {
            request.writeRequestBody(new VertxOutputStream(httpClientRequest));
            return clientResponse;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            httpClientRequest.end();
        }
    }

}
