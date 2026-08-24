# SMPToolkit

SMPToolkit is an all-in-one modular SMP plugin for **Paper 26.2**.

It combines staff tools, moderation, chat control, chat games, player utilities, statistics, gameplay systems, locator events, announcements, Discord logging, and LuckPerms integration in one plugin.

## Requirements

- Paper 26.2
- Java 25+
- LuckPerms recommended
- PlaceholderAPI optional

## Build

```bash
mvn clean package
```

The built plugin will be:

```text
target/SMPToolkit-0.1.jar
```

A GitHub Actions workflow is included and builds the plugin using Java 25.

## Install

1. Put `SMPToolkit-0.1.jar` in your server's `plugins` folder.
2. Start the server once.
3. Edit `plugins/SMPToolkit/config.yml` as needed.
4. Restart the server.

Discord admin-command and Minecraft-chat webhooks are configured in `config.yml`.

See `FEATURES.md` for a short feature overview.
