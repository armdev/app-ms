package net.trajano.ms.sample;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.springframework.beans.factory.annotation.Autowired;

import io.swagger.annotations.Api;
import io.vertx.ext.web.RoutingContext;

@Api
@Path("/hello")
public class Hello {

    private int count;

    @Autowired
    SomeRequestScope req;

    @Autowired
    ISomeAppScope scope;

    @GET
    @Path("/cough")
    public String cough() {

        throw new RuntimeException("ahem");
    }

    @GET
    @Produces("text/plain")
    @Path("/count")
    public Integer getCount() {

        return ++count;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @PermitAll
    public String hello(
        @Context final RoutingContext context) {

        return "Hello" + this + " " + scope + " " + context + " " + req;
        /*
         * @Context final Vertx vertx,
         * @Context final RoutingContext routingContext
         */
        //+ scoped.get()
        //@Context final io.vertx.core.Context vertxContext,
        //  + routingContext;//+ " " + vertx + " " + vertx.getOrCreateContext() + " " + routingContext;
    }

    @GET
    @Path("/json")
    @Produces(MediaType.APPLICATION_JSON)
    public Blah hello2B() {

        return new Blah();
    }

    @GET
    @Path("/xml")
    @Produces(MediaType.APPLICATION_XML)
    public Blah helloB() {

        return new Blah();
    }
}
