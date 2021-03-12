package com.linagora.openpaas.james.app;

import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.blob.deduplication.StorageStrategy;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;

import io.vavr.control.Try;

public class BlobStoreConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlobStoreConfiguration.class);

    @FunctionalInterface
    public interface RequireImplementation {
        RequireCache implementation(BlobStoreImplName implementation);

        default RequireCache cassandra() {
            return implementation(BlobStoreImplName.CASSANDRA);
        }

        default RequireCache s3() {
            return implementation(BlobStoreImplName.S3);
        }
    }

    @FunctionalInterface
    public interface RequireCache {
        RequireStoringStrategy enableCache(boolean enable);

        default RequireStoringStrategy enableCache() {
            return enableCache(CACHE_ENABLED);
        }

        default RequireStoringStrategy disableCache() {
            return enableCache(!CACHE_ENABLED);
        }
    }

    @FunctionalInterface
    public interface RequireStoringStrategy {
        BlobStoreConfiguration strategy(StorageStrategy storageStrategy);

        default BlobStoreConfiguration passthrough() {
            return strategy(StorageStrategy.PASSTHROUGH);
        }

        default BlobStoreConfiguration deduplication() {
            return strategy(StorageStrategy.DEDUPLICATION);
        }
    }

    public static RequireImplementation builder() {
        return implementation -> enableCache -> storageStrategy -> new BlobStoreConfiguration(implementation, enableCache, storageStrategy);
    }

    public enum BlobStoreImplName {
        CASSANDRA("cassandra"),
        S3("s3");

        static String supportedImplNames() {
            return Stream.of(BlobStoreImplName.values())
                .map(BlobStoreImplName::getName)
                .collect(Collectors.joining(", "));
        }

        static BlobStoreImplName from(String name) {
            return Stream.of(values())
                .filter(blobName -> blobName.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format("%s is not a valid name of BlobStores, " +
                    "please use one of supported values in: %s", name, supportedImplNames())));
        }

        private final String name;

        BlobStoreImplName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static final String BLOBSTORE_IMPLEMENTATION_PROPERTY = "implementation";
    static final String CACHE_ENABLE_PROPERTY = "cache.enable";
    static final boolean CACHE_ENABLED = true;
    static final String DEDUPLICATION_ENABLE_PROPERTY = "deduplication.enable";

    public static BlobStoreConfiguration parse(org.apache.james.server.core.configuration.Configuration configuration) throws ConfigurationException {
        PropertiesProvider propertiesProvider = new PropertiesProvider(new FileSystemImpl(configuration.directories()),
            configuration.configurationPath());

        return parse(propertiesProvider);
    }

    public static BlobStoreConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfigurations(ConfigurationComponent.NAMES);
            return BlobStoreConfiguration.from(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ConfigurationComponent.NAME + " configuration file, using cassandra blobstore as the default");
            return BlobStoreConfiguration.builder()
                    .cassandra()
                    .disableCache()
                    .passthrough();
        }
    }

    static BlobStoreConfiguration from(Configuration configuration) {
        BlobStoreImplName blobStoreImplName = Optional.ofNullable(configuration.getString(BLOBSTORE_IMPLEMENTATION_PROPERTY))
            .filter(StringUtils::isNotBlank)
            .map(StringUtils::trim)
            .map(BlobStoreImplName::from)
            .orElseThrow(() -> new IllegalStateException(String.format("%s property is missing please use one of " +
                "supported values in: %s", BLOBSTORE_IMPLEMENTATION_PROPERTY, BlobStoreImplName.supportedImplNames())));

        boolean cacheEnabled = configuration.getBoolean(CACHE_ENABLE_PROPERTY, false);
        boolean deduplicationEnabled = Try.ofCallable(() -> configuration.getBoolean(DEDUPLICATION_ENABLE_PROPERTY))
                .getOrElseThrow(() -> new IllegalStateException("deduplication.enable property is missing please use one of the supported values in: true, false\n" +
                        "If you choose to enable deduplication, the mails with the same content will be stored only once.\n" +
                        "Warning: Once this feature is enabled, there is no turning back as turning it off will lead to the deletion of all\n" +
                        "the mails sharing the same content once one is deleted.\n" +
                        "Upgrade note: If you are upgrading from James 3.5 or older, the deduplication was enabled."));

        if (deduplicationEnabled) {
            return new BlobStoreConfiguration(blobStoreImplName, cacheEnabled, StorageStrategy.DEDUPLICATION);
        } else {
            return new BlobStoreConfiguration(blobStoreImplName, cacheEnabled, StorageStrategy.PASSTHROUGH);
        }
    }

    @VisibleForTesting
    public static RequireStoringStrategy cassandra() {
        return builder()
            .cassandra()
            .disableCache();
    }

    public static RequireCache s3() {
        return builder().s3();
    }

    private final BlobStoreImplName implementation;
    private final boolean cacheEnabled;
    private final StorageStrategy storageStrategy;

    BlobStoreConfiguration(BlobStoreImplName implementation, boolean cacheEnabled, StorageStrategy storageStrategy) {
        this.implementation = implementation;
        this.cacheEnabled = cacheEnabled;
        this.storageStrategy = storageStrategy;
    }

    public boolean cacheEnabled() {
        return cacheEnabled;
    }

    public StorageStrategy storageStrategy() {
        return storageStrategy;
    }

    BlobStoreImplName getImplementation() {
        return implementation;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobStoreConfiguration) {
            BlobStoreConfiguration that = (BlobStoreConfiguration) o;

            return Objects.equals(this.implementation, that.implementation)
                && Objects.equals(this.cacheEnabled, that.cacheEnabled)
                && Objects.equals(this.storageStrategy, that.storageStrategy);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(implementation, cacheEnabled, storageStrategy);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("implementation", implementation)
            .add("cacheEnabled", cacheEnabled)
            .add("storageStrategy", storageStrategy.name())
            .toString();
    }
}