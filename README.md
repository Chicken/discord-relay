# Discord Relay

[![CI](https://github.com/Chicken/discord-relay/actions/workflows/ci.yml/badge.svg)](https://github.com/Chicken/discord-relay/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Chicken/discord-relay/actions/workflows/codeql.yml/badge.svg)](https://github.com/Chicken/discord-relay/actions/workflows/codeql.yml)

> [!NOTE]  
> This is a fork of [viral32111's discord-relay mod](https://github.com/viral32111/discord-relay) tailored for my own needs.

This is a [Minecraft Fabric](https://fabricmc.net/) mod that relays in-game chat messages to and from a Discord channel,
allowing for easy communication between players on a Minecraft server and members in a Discord server.

## Changes compared to the original

- Upgraded dependencies
- Updated for 1.21.1
- Removed unnecessary features
- Removed dependency on viral32111's events mod
- Fixed Discord reconnection bugs
- Added support for Styled Nicknames
- Added support for Vanish
- Added support for using a Thread as a chat channel

## 📥 Usage

<a href="https://modrinth.com/mod/fabric-api/"><img src="https://github.com/viral32111/discord-relay/assets/19510403/2e0d32ee-b4aa-4d93-9388-4a45639c4a96" height="48" alt="Requires Fabric API"></a>
<a href="https://modrinth.com/mod/fabric-language-kotlin"><img src="https://github.com/viral32111/discord-relay/assets/19510403/ab7b8cbb-ff80-4359-8fc9-13a2cf62c4bf" height="48" alt="Requires Fabric Language Kotlin"></a>
<br>

1. Download the JAR file from [the latest release](https://github.com/Chicken/discord-relay/releases/latest).
1. Place the JAR file in the server's `mods` directory.
1. Start the server to initialise the mod for the first time.
1. Configure appropriately in the `config/discordrelay.json` file.
1. Restart the server.

## ⚖️ License

Copyright (C) 2021-2023 [viral32111](https://viral32111.com).  
Copyright (C) 2024 Antti (antti@antti.codes).

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see https://www.gnu.org/licenses.
