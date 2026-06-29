package com.nexgate.config;

import com.nexgate.model.RouteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.InputStream;

public class YamlConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(YamlConfigLoader.class);

    public static RouteConfig load(String path) {
        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(RouteConfig.class, loaderOptions));
        try (InputStream in = new FileInputStream(path)) {
            RouteConfig config = yaml.load(in);
            log.info("Loaded {} routes from {}", 
                config.getRoutes() != null ? config.getRoutes().size() : 0, path);
            return config;
        } catch (Exception e) {
            log.error("Failed to load config from {}", path, e);
            throw new RuntimeException("Failed to load config: " + path, e);
        }
    }
}
