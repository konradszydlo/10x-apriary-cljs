# Apriary Application

This is an apiary application built in Clojure with [Biff](https://biffweb.com).
It's a simple app to manage notes about beehives.

The project is created as a part of an AI course [10xDevs](https://www.10xdevs.pl/). 

## Getting Started

### Prerequisites

#### Clojure or Docker

This is a Clojure project. You need to have [Clojure](https://www.clojure.org/guides/getting_started) installed.

If you're new to Clojure and just want to run the application, you can use the Docker image.

#### Configuration

- `config.env`
You can copy `config.env.example` to `config.env`.


### Development

Run `clj -M:dev dev` to get started. See `clj -M:dev --help` for other commands.

```shell
cljfmt check

cljfmt fix

clj-kondo --lint src test
```

### Docker

See DOCKER.md for detailed guide on running the application with Docker.

#### Summary

```shell
docker build -t apiary-app .

docker compose up
```

Navigate to [localhost](https://localhost/).

You might see a warning about a self-signed certificate. Ignore it.