package org.jasig.cas.util;

import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ServletContextAware;

import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.validation.constraints.NotNull;
import java.net.URL;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Initializes the CAS logging framework by calling
 * the logger initializer and sets the location of the
 * log configuration file.
 * @author Misagh Moayyed
 * @since 4.1
 */
@Component("log4jInitialization")
public final class CasLoggerContextInitializer implements ServletContextAware {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(CasLoggerContextInitializer.class);

    private ServletContext context;

    private ServletContextListener loggerContext;

    @Value("${log4j.config.package:org.apache.logging.log4j.web}")
    private final String loggerContextPackageName;

    @Value("${log4j.config.location:classpath:log4j2.xml}")
    private final Resource logConfigurationFile;

    @Value("${log4j.config.field:log4jConfiguration}")
    private final String logConfigurationField;

    /**
     * Instantiates a new Cas logger context initializer.
     */
    protected CasLoggerContextInitializer() {
        this.loggerContext = null;
        this.loggerContextPackageName = null;
        this.logConfigurationField = null;
        this.logConfigurationFile = null;
    }

    /**
     * Instantiates a new Cas logger context initializer.
     *
     * @param loggerContextPackageName the logger context package name
     * @param logConfigurationFile the log configuration file
     * @param logConfigurationField the log configuration field
     */
    public CasLoggerContextInitializer(@NotNull final String loggerContextPackageName,
                                       @NotNull final Resource logConfigurationFile,
                                       @NotNull final String logConfigurationField) {
        this.loggerContextPackageName = loggerContextPackageName;
        this.logConfigurationField = logConfigurationField;
        this.logConfigurationFile = logConfigurationFile;
    }

    /**
     * Initialize the logger by decorating the context
     * with settings for the log file and context.
     * Calls the initializer of the logging framework
     * to start the logger.
     */
    private void initialize() {
        try {
            if (!INITIALIZED.get() && this.loggerContext != null) {
                final ServletContextEvent event = new ServletContextEvent(this.context);
                this.loggerContext.contextInitialized(event);
                LOGGER.debug("Initialized logging context via [{}]. Logs will be written to [{}]",
                        this.loggerContext.getClass().getSimpleName(),
                        this.logConfigurationFile);
                INITIALIZED.set(true);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Destroys all logging hooks and shuts down
     * the logger.
     */
    @PreDestroy
    public void destroy() {
        try {
            if (INITIALIZED.get() && this.loggerContext != null) {
                final ServletContextEvent event = new ServletContextEvent(this.context);
                LOGGER.debug("Destroying logging context and shutting it down");
                this.loggerContext.contextDestroyed(event);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Prepares the logger context. Locates the context and
     * sets the configuration file.
     * @return the logger context
     */
    private ServletContextListener prepareAndgetContextListener() {
        try {
            if (this.logConfigurationFile == null || !this.logConfigurationFile.exists()) {
                throw new RuntimeException("Log4j configuration file cannot be located");
            }

            if (StringUtils.isNotBlank(this.loggerContextPackageName)) {
                final Collection<URL> set = ClasspathHelper.forPackage(this.loggerContextPackageName);
                final Reflections reflections = new Reflections(
                    new ConfigurationBuilder().addUrls(set).setScanners(new SubTypesScanner()));
                final Set<Class<? extends ServletContextListener>> subTypesOf =
                    reflections.getSubTypesOf(ServletContextListener.class);
                if (subTypesOf.isEmpty()) {
                    throw new IllegalArgumentException("No context listeners could be found for "
                        + this.loggerContextPackageName);
                }
                final ServletContextListener loggingContext = subTypesOf.iterator().next().newInstance();
                this.context.setInitParameter(this.logConfigurationField,
                    this.logConfigurationFile.getURI().toString());
                return loggingContext;
            }
            return null;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>Prepared the logger context with the
     * received servlet web context. Because the context
     * may be initialized twice, there are safety checks
     * added to ensure we don't create duplicate log
     * environments.</p>
     * @param servletContext the servlet context
     */
    @Override
    public void setServletContext(final ServletContext servletContext) {
        this.context = servletContext;
        this.loggerContext = prepareAndgetContextListener();
        initialize();
    }
}
