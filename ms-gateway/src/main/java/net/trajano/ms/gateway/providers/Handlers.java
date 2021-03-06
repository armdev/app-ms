package net.trajano.ms.gateway.providers;

import static io.vertx.core.http.HttpHeaders.AUTHORIZATION;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpHeaders.DATE;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static net.trajano.ms.gateway.internal.MediaTypes.APPLICATION_FORM_URLENCODED;
import static net.trajano.ms.gateway.internal.MediaTypes.APPLICATION_JSON;
import static net.trajano.ms.gateway.providers.RequestIDProvider.REQUEST_ID;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import net.trajano.ms.gateway.internal.Conversions;
import net.trajano.ms.gateway.internal.Errors;
import net.trajano.ms.gateway.internal.MediaTypes;

@Configuration
@Component
public class Handlers {

    private static final String BEARER_TOKEN_PATTERN = "^Bearer ([A-Za-z0-9]{64})$";

    private static final String INTERNAL_SERVER_ERROR = "Internal Server Error";

    private static final Logger LOG = LoggerFactory.getLogger(Handlers.class);

    private static final Set<CharSequence> RESTRICTED_HEADERS;

    private static final String X_JWKS_URI = "X-JWKS-URI";

    private static final String X_JWT_ASSERTION = "X-JWT-Assertion";

    private static final String X_JWT_AUDIENCE = "X-JWT-Audience";

    static {
        RESTRICTED_HEADERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(X_JWKS_URI, X_JWT_ASSERTION, X_JWT_AUDIENCE, REQUEST_ID, AUTHORIZATION, DATE)));
    }

    /**
     * Allowed origins for {@link HttpHeaders#ACCESS_CONTROL_ALLOW_ORIGIN}
     */
    @Value("${allowedOrigins:*}")
    private String allowedOrigins;

    @Value("${authorization.token_endpoint}")
    private URI authorizationEndpoint;

    /**
     * This is the Authorization header value for the gateway when requesting the
     * JWT data from the authorization server.
     */
    private String gatewayClientAuthorization;

    /**
     * Gateway client ID. The gateway has it's own client ID because it is the only
     * one that should be authorized to get the id_token from an authorization_code
     * request to the authorization server token endpoint.
     */
    @Value("${authorization.client_id}")
    private String gatewayClientId;

    /**
     * Gateway client secret
     */
    @Value("${authorization.client_secret}")
    private String gatewayClientSecret;

    @Autowired
    private HttpClient httpClient;

    @Value("${jwks.path}")
    private String jwksPath;

    @Value("${jwks.uri}")
    private URI jwksUri;

    @Autowired
    private RequestIDProvider requestIDProvider;

    public Handler<RoutingContext> failureHandler() {

        return context -> {
            LOG.error("Unhandled server exception", context.failure());
            if (!context.response().ended()) {
                if (context.failure() instanceof ConnectException) {
                    context.response().setStatusCode(504)
                        .setStatusMessage("Gateway Timeout")
                        .putHeader(CONTENT_TYPE, MediaTypes.APPLICATION_JSON)
                        .end(Errors.serverError("Gateway Timeout").toBuffer());
                } else if (context.failure() instanceof UnknownHostException) {
                    context.response().setStatusCode(503)
                        .setStatusMessage("Gateway Error")
                        .putHeader(CONTENT_TYPE, MediaTypes.APPLICATION_JSON)
                        .end(Errors.serverError("Gateway Error").toBuffer());
                } else {
                    context.response().setStatusCode(500)
                        .setStatusMessage(INTERNAL_SERVER_ERROR)
                        .putHeader(HttpHeaders.CONTENT_TYPE, MediaTypes.APPLICATION_JSON)
                        .end(Errors.serverError(INTERNAL_SERVER_ERROR).toBuffer());
                }
            }
        };

    }

    /**
     * Obtains the access token from the request. It is expected to be the
     * Authorization with a bearer tag. The authentication code is expected to be a
     * given pattern.
     *
     * @param contextRequest
     *            request
     * @return access token
     */
    private String getAccessToken(final HttpServerRequest contextRequest) {

        final String authorizationHeader = contextRequest.getHeader(AUTHORIZATION);
        if (authorizationHeader == null) {
            return null;
        }
        final Matcher m = Pattern.compile(BEARER_TOKEN_PATTERN).matcher(authorizationHeader);
        if (!m.matches()) {
            return null;
        } else {
            return m.group(1);
        }
    }

    @PostConstruct
    public void init() {

        gatewayClientAuthorization = "Basic " + Base64.getEncoder().encodeToString((gatewayClientId + ":" + gatewayClientSecret).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Checks if the header should be forwarded or stripped from the forwarded
     * request.
     *
     * @param headerName
     *            header name
     * @return true if the header is restricted
     */
    private boolean isHeaderFowardable(final String headerName) {

        return !RESTRICTED_HEADERS.contains(headerName);
    }

    /**
     * This handler goes through the authorization to prepopulate the
     * X-JWT-Assertion header.
     *
     * @return handler
     */
    @SuppressWarnings("unchecked")
    public Handler<RoutingContext> protectedHandler(final String baseUri,
        final URI endpoint) {

        return context -> {
            final HttpServerRequest contextRequest = context.request();
            final HttpServerResponse contextResponse = context.response();

            if (!contextRequest.uri().startsWith(baseUri)) {
                throw new IllegalStateException(contextRequest.uri() + " did not start with" + baseUri);
            }

            final String accessToken = getAccessToken(contextRequest);

            final String requestID = requestIDProvider.newRequestID(context);
            final String now = RFC_1123_DATE_TIME.format(now(UTC));

            if (accessToken == null) {
                LOG.debug("missing or invalid access token");
                contextResponse
                    .setStatusCode(401)
                    .setStatusMessage("Unauthorized")
                    .putHeader("WWW-Authenticate", "Bearer")
                    .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                    .end(Errors.unauthorizedClient("missing or invalid access token").toBuffer());
                return;
            }

            contextRequest.setExpectMultipart(context.parsedHeaders().contentType().isPermitted() && "multipart".equals(context.parsedHeaders().contentType().component()));
            contextRequest.pause();
            LOG.debug("access_token={}", accessToken);
            final RequestOptions clientRequestOptions = Conversions.toRequestOptions(endpoint, contextRequest.uri().substring(baseUri.length()));

            final HttpClientRequest authorizationRequest = httpClient.post(Conversions.toRequestOptions(authorizationEndpoint), authorizationResponse ->
            // Trust the authorization endpoint not to give a large body
            authorizationResponse.bodyHandler(buffer -> {

                if (authorizationResponse.statusCode() != 200) {
                    authorizationResponse.headers().forEach(h -> stripTransferEncodingAndLength(contextResponse, h));
                    contextResponse.setStatusCode(authorizationResponse.statusCode())
                        .setStatusMessage(authorizationResponse.statusMessage())
                        .setChunked(false)
                        .end(buffer);
                    contextRequest.resume();
                } else {
                    final JsonObject response = new JsonObject(buffer);
                    final String idToken = response.getString("id_token");

                    final List<String> audience = response.getJsonArray("aud").getList();
                    if (idToken == null) {
                        LOG.error("Unable to get the ID Token from {} given access_token={}", authorizationEndpoint, accessToken);
                        context.response().setStatusCode(500)
                            .setStatusMessage(INTERNAL_SERVER_ERROR)
                            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                            .end(Errors.serverError("Unable to get assertion from authorization endpoint").toBuffer());
                        return;
                    }

                    final HttpClientRequest clientRequest = httpClient.request(contextRequest.method(), clientRequestOptions, clientResponse -> {
                        contextResponse.setChunked(clientResponse.getHeader(HttpHeaders.CONTENT_LENGTH) == null)
                            .setStatusCode(clientResponse.statusCode());
                        clientResponse.headers().forEach(e -> contextResponse.putHeader(e.getKey(), e.getValue()));
                        clientResponse.handler(contextResponse::write)
                            .endHandler(v -> contextResponse.end());
                    }).exceptionHandler(context::fail);

                    contextRequest.headers().forEach(e -> {
                        if (isHeaderFowardable(e.getKey())) {
                            clientRequest.putHeader(e.getKey(), e.getValue());
                        }
                    });
                    clientRequest.putHeader(X_JWT_ASSERTION, idToken)
                        .putHeader(X_JWKS_URI, jwksUri.toASCIIString())
                        .putHeader(X_JWT_AUDIENCE, audience.stream().collect(Collectors.joining(", ")))
                        .putHeader(REQUEST_ID, requestID)
                        .putHeader(DATE, now);
                    contextRequest.resume();
                    contextRequest.handler(clientRequest::write)
                        .endHandler(v -> clientRequest.end())
                        .exceptionHandler(context::fail);
                }
            }).exceptionHandler(context::fail)).exceptionHandler(context::fail);

            authorizationRequest
                .putHeader(AUTHORIZATION, gatewayClientAuthorization)
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_FORM_URLENCODED)
                .putHeader(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .putHeader(REQUEST_ID, requestID)
                .putHeader(DATE, now)
                .end("grant_type=authorization_code&code=" + accessToken);

        };
    }

    private void stripTransferEncodingAndLength(final HttpServerResponse contextResponse,
        final Entry<String, String> h) {

        if ("Content-Length".equalsIgnoreCase(h.getKey()) || "Transfer-Encoding".equalsIgnoreCase(h.getKey())) {
            return;
        }
        contextResponse.putHeader(h.getKey(), h.getValue());
    }

    /**
     * This handler passes the data through.
     *
     * @param baseUri
     *            base URI
     * @param endpoint
     *            endpoint
     * @return handler
     */
    public Handler<RoutingContext> unprotectedHandler(final String baseUri,
        final URI endpoint) {

        return context -> {
            final HttpServerRequest contextRequest = context.request();
            if (!contextRequest.uri().startsWith(baseUri)) {
                throw new IllegalStateException(contextRequest.uri() + " did not start with" + baseUri);
            }
            final String requestID = requestIDProvider.newRequestID(context);
            contextRequest.setExpectMultipart(context.parsedHeaders().contentType().isPermitted() && "multipart".equals(context.parsedHeaders().contentType().component()));
            final RequestOptions clientRequestOptions = Conversions.toRequestOptions(endpoint, contextRequest.uri().substring(baseUri.length()));
            final HttpClientRequest clientRequest = httpClient.request(contextRequest.method(), clientRequestOptions, clientResponse -> {
                contextRequest.response().setChunked(clientResponse.getHeader(HttpHeaders.CONTENT_LENGTH) == null)
                    .setStatusCode(clientResponse.statusCode());
                clientResponse.headers().forEach(e -> contextRequest.response().putHeader(e.getKey(), e.getValue()));
                contextRequest.response()
                    .putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigins);
                clientResponse.handler(contextRequest.response()::write)
                    .endHandler(v -> contextRequest.response().end());
            }).exceptionHandler(context::fail)
                .setChunked(true);

            contextRequest.headers().forEach(e -> {
                if (isHeaderFowardable(e.getKey())) {
                    clientRequest.putHeader(e.getKey(), e.getValue());
                }
            });

            clientRequest.putHeader(REQUEST_ID, requestID);
            clientRequest.putHeader(DATE, RFC_1123_DATE_TIME.format(now(UTC)));
            contextRequest.handler(clientRequest::write)
                .endHandler(v -> clientRequest.end());

        };
    }
}
