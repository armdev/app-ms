package net.trajano.ms.engine.internal.resteasy;

import java.util.concurrent.TimeUnit;

import org.jboss.resteasy.core.AbstractExecutionContext;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyAsynchronousResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.ext.web.RoutingContext;

public class VertxExecutionContext extends AbstractExecutionContext {

    private static final Logger LOG = LoggerFactory.getLogger(VertxExecutionContext.class);

    private VertxAsynchronousResponse asynchronousResponse;

    private final RoutingContext routingContext;

    private final VertxHttpResponse vertxResponse;

    public VertxExecutionContext(final RoutingContext routingContext,
        final SynchronousDispatcher dispatcher,
        final HttpRequest request,
        final VertxHttpResponse response) {

        super(dispatcher, request, response);
        this.routingContext = routingContext;
        vertxResponse = response;
    }

    @Override
    public ResteasyAsynchronousResponse getAsyncResponse() {

        return asynchronousResponse;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This is implemented as a check to whether an asynchronous response was
     * initialized by either {@link #suspend()}, {@link #suspend(long)} or
     * {@link #suspend(long, TimeUnit)}.
     */
    @Override
    public boolean isSuspended() {

        return asynchronousResponse != null;
    }

    @Override
    public ResteasyAsynchronousResponse suspend() {

        asynchronousResponse = new VertxAsynchronousResponse(dispatcher, request, vertxResponse, routingContext);
        LOG.debug("asynchronousResponse={} created", asynchronousResponse);
        return asynchronousResponse;
    }

    @Override
    public ResteasyAsynchronousResponse suspend(final long millis) {

        return suspend(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public ResteasyAsynchronousResponse suspend(final long time,
        final TimeUnit unit) {

        asynchronousResponse = new VertxAsynchronousResponse(dispatcher, request, vertxResponse, routingContext);
        LOG.debug("asynchronousResponse={} created", asynchronousResponse);
        asynchronousResponse.setTimeout(time, unit);
        return asynchronousResponse;

    }

}
