package ru.leymooo.simpleautomessages;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Plugin(id = "simpleautomessages", name = "SimpleAutoMessages", version = "1.1",
        description = "AutoMessages plugin for velocity",
        authors = "Leymooo")
public class SimpleAutoMessages {

    private final ProxyServer server;
    private final Logger logger;
    private final List<AutoMessage> messages;

    private final YamlConfigurationLoader configurationLoader = YamlConfigurationLoader.builder().path(Paths.get("config.yml")).build();
    private CommentedConfigurationNode root;
    private ScheduledTask task;

    private final Path dataDirectory;

    @Inject
    public SimpleAutoMessages(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        this.messages = new ArrayList<>();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        saveDefaultConfig();
        try {
            root = configurationLoader.load();
        } catch (ConfigurateException e){
            logger.error("There is a problem with the configuration", e);
        }

        createAutoMessages();
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        logger.info("Reloading");
        try {
            root = configurationLoader.load();
        } catch (ConfigurateException e){
            logger.error("There is a problem with reloading the configuration", e);
        }
        messages.forEach(AutoMessage::stopScheduler);
        logger.info("Plugin will be reloaded after 3 seconds");
        if (task != null) {
            task.cancel();
        }
        createAutoMessages();
    }

    @Subscribe
    public void onShutDown(ProxyShutdownEvent ev) {
        logger.info("Disabling SimpleAutoMessages");
        messages.forEach(AutoMessage::stopScheduler);
        logger.info("SimpleAutoMessages disabled");
    }


    private void createAutoMessages() {
        this.task = server.getScheduler().buildTask(this, new AutoMessageRunnable()).delay(3, TimeUnit.SECONDS).schedule();
    }

    public class AutoMessageRunnable implements Runnable {

        @Override
        public void run() {
            logger.info("Staring AutoMessages tasks");
            synchronized (messages) {
                messages.clear();
                for (CommentedConfigurationNode node : root.childrenList()) {
                    AutoMessage am;
                    try {
                        am = AutoMessage.fromConfiguration(node, server);
                    } catch (SerializationException e) {
                        e.printStackTrace();
                        return;
                    }
                    CheckResult result = am.checkAndRun(server.getPluginManager().fromInstance(SimpleAutoMessages.this).get());
                    switch (result) {
                        case OK:
                            logger.info("'{}' was started", node.getString());
                            messages.add(am);
                            continue;
                        case INTERVAL_NOT_SET:
                            logger.warn("Interval for '{}' is not specified or <=0", node.getString());
                            break;
                        case NO_MESSAGES:
                            logger.warn("Messages for '{}' is not specified or empty", node.getString());
                            break;
                        case NO_SERVERS:
                        default:
                            logger.warn("Servers for '{}' is not specified or empty", node.getString());
                            break;
                    }
                    logger.warn("'{}' was not started", node.getString());
                }
                logger.info("Done");
            }
        }

    }

    private void saveDefaultConfig() {
        File config = new File(dataDirectory.toFile(), "config.yml");
        config.getParentFile().mkdir();
        try {
            if (!config.exists()) {
                try (InputStream in = SimpleAutoMessages.class.getClassLoader().getResourceAsStream("config.yml")) {
                    Files.copy(in, config.toPath());
                }
            }
        } catch (Exception ex) {
            logger.error("Can not save config", ex);
        }
    }
}
