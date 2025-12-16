# PollPlugin

A GUI-based polling system for Minecraft servers with MongoDB storage.

## Features

- Interactive menu interface for creating and voting
- Up to 6 options per poll with custom duration
- Real-time results with progress bars
- Permission-based access control
- Automatic poll expiration
- Poll history and statistics

## Requirements

- Bukkit/Spigot/Paper 1.16+
- MongoDB 4.0+
- MongoDB Java Driver

## Installation

1. Place the plugin JAR in your `plugins` folder
2. Install MongoDB Java Driver (if not bundled)
3. Start server to generate config
4. Configure MongoDB connection
5. Restart server

## Configuration

Edit `config.yml`:

```yaml
mongodb:
  connection-string: "mongodb://localhost:27017"
  database: "pollplugin"
  collection: "polls"

poll:
  max-question-length: 200
  min-question-length: 5
  creation-cooldown-seconds: 60
```

## Commands

- `/poll` - Open poll interface
- `/poll list` - Show active polls
- `/poll closed` - View poll history
- `/poll close <id>` - Close a poll
- `/poll remove <id>` - Delete a poll
- `/createpoll <duration> <question>` - Create new poll

**Duration examples:** `30m`, `2h`, `1d`, `7d`

## Permissions

- `poll.view` - View polls (default: all)
- `poll.vote` - Vote in polls (default: all)
- `poll.create` - Create polls (default: op)
- `poll.close` - Close any poll
- `poll.remove` - Delete any poll

## Basic Usage

**Create a poll:**
```
/createpoll 1d Should we add a new minigame?
```

**Vote:** Use `/poll` and click on polls to vote

**Manage:** Poll creators can close/delete their own polls

## Troubleshooting

- **Plugin won't start:** Check MongoDB connection and driver installation
- **Can't create polls:** Verify `poll.create` permission and cooldown
- **Database errors:** Ensure MongoDB is running and accessible

## Support

Report issues with server version, error logs, and reproduction steps.
