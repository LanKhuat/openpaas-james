# OpenPaaS James

This project adapts and enhance [Apache James project](https://james.apache.org)

## Useful links

 - We maintain a [CHANGELOG](CHANGELOG.md) and [upgrade instructions](upgrade-instructions.md)

 - [Building + Running the memory server](openpaas-james/apps/memory/README.md)

## Additional features

Additional features includes:
 - JMAP PUSH over AMQP (WIP)
 - JMAP Filters/get and Filters/set (WIP)

## Building the project

This projects uses git submodules to track the latest branch of [the Apache James project](https://james.apache.org)

You need to retrieve Apache sources first:

```
cd james-project
git fetch
git checkout master
```

Then you can compile both `apache/james-project` and `linagora/openpaas-james` together.

```
mvn clean package -Dmaven.javadoc.skip=true
```