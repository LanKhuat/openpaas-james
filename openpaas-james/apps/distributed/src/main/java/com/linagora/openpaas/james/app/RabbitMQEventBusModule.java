package com.linagora.openpaas.james.app;

import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.event.json.MailboxEventSerializer;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.KeyReconnectionHandler;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RegistrationKey;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class RabbitMQEventBusModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MailboxEventSerializer.class).in(Scopes.SINGLETON);
        bind(EventSerializer.class).to(MailboxEventSerializer.class);

        bind(NamingStrategy.class).toInstance(new NamingStrategy("mailboxEvent"));
        bind(RabbitMQEventBus.class).in(Scopes.SINGLETON);
        bind(EventBus.class).to(RabbitMQEventBus.class);

        Multibinder.newSetBinder(binder(), RegistrationKey.Factory.class)
            .addBinding().to(MailboxIdRegistrationKey.Factory.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);
        bind(EventBusId.class).toInstance(EventBusId.random());

        Multibinder<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlerMultibinder = Multibinder.newSetBinder(binder(), SimpleConnectionPool.ReconnectionHandler.class);
        reconnectionHandlerMultibinder.addBinding().to(KeyReconnectionHandler.class);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(RabbitMQEventBus instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(instance::start);
    }
}