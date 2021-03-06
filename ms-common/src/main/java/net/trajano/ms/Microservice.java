package net.trajano.ms;

import static java.lang.String.format;

import java.util.Iterator;
import java.util.ServiceLoader;

import javax.ws.rs.core.Application;

import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;

import net.trajano.ms.spi.MicroserviceEngine;

public class Microservice {

    protected static Class<? extends Application> applicationClass;

    /**
     * Microservice engine to use.
     */
    private static ServiceLoader<MicroserviceEngine> microserviceEngineLoader = ServiceLoader.load(MicroserviceEngine.class);

    /**
     * This returns the application class that was set, may be <code>null</code>.
     *
     * @return the application class.
     */
    public static Class<? extends Application> getApplicationClass() {

        return applicationClass;
    }

    /**
     * Bootstrap the microservice application.
     *
     * @param applicationClass
     *            JAX-RS Application class
     * @param extraSources
     *            extra source classes.
     * @param args
     *            command line arguments
     */
    public static void run(final Class<? extends Application> applicationClass,
        final Class<?>[] extraSources,
        final String... args) {

        final Iterator<MicroserviceEngine> it = microserviceEngineLoader.iterator();
        if (!it.hasNext()) {
            throw new LinkageError("No MicroserviceEngine was defined");
        }
        final MicroserviceEngine microserviceEngine = it.next();
        if (it.hasNext()) {
            throw new LinkageError(format("Multiple MicroserviceEngine was defined, %s, %s and possibly more", microserviceEngine, it.next()));
        }
        if (Microservice.applicationClass != null) {
            throw new LinkageError("Another Application class has already been registered in this JVM.");
        }
        Microservice.applicationClass = applicationClass;

        final Object[] bootstrapObjects = microserviceEngine.bootstrap();
        final Object[] sources = new Object[extraSources.length + bootstrapObjects.length];

        System.arraycopy(extraSources, 0, sources, 0, extraSources.length);
        System.arraycopy(bootstrapObjects, 0, sources, extraSources.length, bootstrapObjects.length);

        final SpringApplication springApplication = new SpringApplication(sources);
        springApplication.setWebEnvironment(false);
        springApplication
            .setBannerMode(Mode.OFF);
        springApplication.run(args);
    }

    /**
     * Bootstrap the microservice application.
     *
     * @param applicationClass
     *            JAX-RS Application class
     * @param args
     *            command line arguments
     */
    public static void run(final Class<? extends Application> applicationClass,
        final String... args) {

        run(applicationClass, new Class<?>[0], args);
    }

    /**
     * Prevent instantiation. Only use {@link #run(Class, String...)}
     */
    protected Microservice() {

    }

}
