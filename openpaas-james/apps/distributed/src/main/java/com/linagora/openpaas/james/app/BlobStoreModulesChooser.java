package com.linagora.openpaas.james.app;

import java.util.List;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.cassandra.CassandraBlobStoreDAO;
import org.apache.james.blob.cassandra.cache.CachedBlobStore;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.mailbox.CassandraBlobStoreDependenciesModule;
import org.apache.james.modules.mailbox.CassandraBucketModule;
import org.apache.james.modules.objectstorage.DefaultBucketModule;
import org.apache.james.modules.objectstorage.S3BlobStoreModule;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class BlobStoreModulesChooser {
    static class CassandraBlobStoreDAODeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new CassandraBlobStoreDependenciesModule());
            install(new CassandraBucketModule());

            bind(BlobStoreDAO.class).to(CassandraBlobStoreDAO.class);
        }
    }

    static class ObjectStorageBlobStoreDAODeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new S3BlobStoreModule());
            install(new DefaultBucketModule());

            bind(BlobStoreDAO.class).to(S3BlobStoreDAO.class);
        }
    }

    static class StoragePolicyConfigurationSanityEnforcementModule extends AbstractModule {
        private BlobStoreConfiguration choosingConfiguration;

        StoragePolicyConfigurationSanityEnforcementModule(BlobStoreConfiguration choosingConfiguration) {
            this.choosingConfiguration = choosingConfiguration;
        }

        @Override
        protected void configure() {
            Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {});
            eventDTOModuleBinder.addBinding().toInstance(StorageStrategyModule.STORAGE_STRATEGY);

            bind(BlobStoreConfiguration.class).toInstance(choosingConfiguration);
            bind(EventsourcingStorageStrategy.class).in(Scopes.SINGLETON);

            Multibinder.newSetBinder(binder(), StartUpCheck.class)
                .addBinding()
                .to(BlobStoreConfigurationValidationStartUpCheck.class);
        }
    }

    @VisibleForTesting
    public static List<Module> chooseModules(BlobStoreConfiguration choosingConfiguration) {
        return ImmutableList.<Module>builder()
            .add(chooseBlobStoreDAOModule(choosingConfiguration.getImplementation()))
            .add(chooseStoragePolicyModule(choosingConfiguration.storageStrategy()))
            .add(new StoragePolicyConfigurationSanityEnforcementModule(choosingConfiguration))
            .build();
    }

    public static Module chooseBlobStoreDAOModule(BlobStoreConfiguration.BlobStoreImplName implementation) {
        switch (implementation) {
            case CASSANDRA:
                return new CassandraBlobStoreDAODeclarationModule();
            case S3:
                return new ObjectStorageBlobStoreDAODeclarationModule();
            default:
                throw new RuntimeException("Unsupported blobStore implementation " + implementation);
        }
    }

    private static Module chooseStoragePolicyModule(StorageStrategy storageStrategy) {
        switch (storageStrategy) {
            case DEDUPLICATION:
                return binder -> binder.bind(BlobStore.class)
                    .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                    .to(DeDuplicationBlobStore.class);
            case PASSTHROUGH:
                return binder -> binder.bind(BlobStore.class)
                    .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                    .to(PassThroughBlobStore.class);
            default:
                throw new RuntimeException("Unknown storage strategy " + storageStrategy.name());
        }
    }
}
