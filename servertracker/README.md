# servertracker

Server-only Fabric 26.2 mod that records:

- player chat messages
- player joins and leaves
- commands executed by players

It does not copy the Minecraft console log, status messages, advancement
announcements, operator broadcasts, or other game/system messages.

Logs are appended as UTF-8 to:

```text
config/servertracker/chat/chatlog-YYYY-MM-DD.log
```

Each entry includes the server machine's local time:

```text
[14:03:27] [CHAT] PlayerName: Hello!
[14:04:10] [COMMAND] PlayerName: /spawn
[14:05:02] [LEAVE] PlayerName
```

A new dated file is selected automatically after midnight. The mod requires
Fabric API and Fabric Language Kotlin on the dedicated server. Clients do not
need ServerTracker.

## License

CC0-1.0
