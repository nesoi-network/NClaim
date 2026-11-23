# NClaim – Advanced Chunk Claim Plugin for Minecraft

[![spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/nclaim-advanced-claim-system.122527/)
[![builtbybit](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/builtbybit_vector.svg)](https://builtbybit.com/resources/nclaim-advanced-claim-system.60265/)
[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/nclaim)

NClaim is a fully customizable chunk claim plugin for Paper and Folia servers. Players claim chunks to protect their builds, invite co-op members with fine-grained permissions, and manage everything from in-game menus.

[![discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/social/discord-plural_vector.svg)](https://discord.gg/qcW6YrxwqJ)

---

## ⚙️ Features

- 🏷️ **Chunk claims:** claim, expand, extend, rename, sell or delete claims, also remotely from the menu.
- 👥 **Co-op:** add friends to a claim with 53 individual permissions in 9 categories.
- 🛡️ **Claim settings:** PvP, explosions, mob spawning, mob griefing, fire spread, crop trampling and more, per claim.
- 🧱 **Claim blocks:** players unlock and switch claim blocks; admins add new ones in game, for every claim or for one claim.
- 💎 **Claim value:** valuable blocks inside a claim raise its value (`block_levels.yml`), shown in menus, holograms and placeholders.
- 🧭 **Borders:** see-through coloured walls show your claims, co-op claims, other claims and free land.
- ⏳ **Expiration:** claims expire after a configurable time unless extended.
- 🌍 **World / WorldGuard region blacklists.**
- 📦 **Storage:** YAML, SQLite or MySQL.
- 🪧 **Built-in holograms:** vanilla text displays, no hologram plugin needed. Choose, order and add your own lines (colours, PlaceholderAPI), and set height, size and background in `hologram_settings`.
- 🎨 **Custom menu items:** menu icons can be any item, custom model data, a resource pack item model, a player head, or an ItemsAdder, Nexo or Oraxen item (see the top of `guis.yml`).
- 🧵 **Folia ready:** world access runs on the owning region thread, disk and database work runs off the main thread.
- 🔄 **Safe updates:** new config entries are added without touching your values.

---

## 🛠️ Installation

**Requirements:** [Paper](https://papermc.io/software/paper) or [Folia](https://papermc.io/software/folia) **1.21.4 or newer** (including 26.x), Java 21+.
Spigot and Minecraft versions before 1.21.4 are no longer supported; use NClaim 3.x for those.

1. Download the latest version from [GitHub Releases](https://github.com/aysihuniks/NClaim/releases).
2. Put the `.jar` into your server's `plugins` folder and restart.
3. Edit `config.yml` (every option is explained in the file) and apply changes with `/nclaim admin reload`.

**Optional plugins:** [Vault](https://www.spigotmc.org/resources/vault.34315/) (economy), [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (placeholders),
WorldGuard (region blacklist), SkinsRestorer (head skins), AxSellwands, [Farmer](https://github.com/Geik-xyz/Farmer-v6)
(one farmer per claim, co-ops are added as farmer users; add your claim worlds to `allowed-worlds` in Farmer's `config.yml`),
[PvPManager](https://modrinth.com/plugin/pvpmanager) (claims with PvP off are safe zones: combat tagged players are pushed back).

### Upgrading from 3.x

- Existing `claims.yml`, `players/` and database data are read as-is. Claims in the nether/end that older versions failed to load are recovered.
- `config.yml` is kept; new keys are added. `guis.yml` and your language file are **replaced** once (menus and messages were redesigned). The old files are saved to `backups/`.
- Switching `database.enable` to `true` with an empty database copies the YAML data into it.
- DecentHolograms / FancyHolograms are no longer used and can be removed.
- Full list of changes: [CHANGELOG.md](CHANGELOG.md).

### Building

```bash
./gradlew build          # -> build/libs/NClaim-<version>.jar
./gradlew runServer      # Paper test server
./gradlew runFolia       # Folia test server
```

---

## 📝 Commands

| Command                | Description                           | Permission          | 
|------------------------|---------------------------------------|---------------------|
| `/nclaim`              | Opens the claim menu                  | `nclaim.use`        |
| `/nclaim help` / `?`   | Shows the help menu                   | `nclaim.help`       | 
| `/nclaim list`         | Lists your claims                     | `nclaim.use`        |
| `/nclaim border`       | Shows / hides the coloured border walls of the claim you stand in | `nclaim.use` |
| `/nclaim about`        | Plugin information                    |  `-`                | 
| `/nclaim level`        | View claim value and info             | `nclaim.level`      |
| `/nclaim balance`      | Shows your balance                    | `nclaim.balance`    |   
| `/nclaim name <claim\|here> <new_name\|off>` | Change claim name (`off` resets it) | `nclaim.name` |       
| `/nclaim admin`        | Access admin commands                 | `nclaim.admin`      |        
| `/nclaim admin reload` | Reloads configuration (also works from the console) | `nclaim.reload` |

---

## 🔐 Permissions

> The version column shows when the permission was added.

| Permission                         | Description                                                          | Default   | Version |
|------------------------------------|----------------------------------------------------------------------|-----------|---------|
| nclaim.help                        | Access help commands                                                 | false     | 1.0     |
| nclaim.balance                     | View claim balance                                                   | false     | 1.0     |
| nclaim.buy                         | Buy new claims                                                       | false     | 1.0     |
| nclaim.admin                       | Access all admin commands                                            | false     | 1.0     |
| nclaim.reload                      | Reload plugin configuration                                          | false     | 1.0     |
| nclaim.add                         | Access /nclaim admin add command                                     | false     | 1.0     |
| nclaim.remove                      | Access /nclaim admin remove command                                  | false     | 1.0     |
| nclaim.set                         | Access /nclaim admin set command                                     | false     | 1.0     |
| nclaim.change                      | Change plugin settings (language, blockvalue etc.)                   | false     | 1.0     |
| nclaim.bypass.*                    | Access all bypass permissions                                        | false     | 1.0     |
| nclaim.bypass.claim_buy_price      | Bypass claim purchase price                                          | false     | 1.0     |
| nclaim.bypass.land_buy_price       | Bypass land expansion price                                          | false     | 1.0     |
| nclaim.bypass.max_claim_count      | Bypass maximum number of claims limit                                | false     | 1.3     |
| nclaim.adminmenu                   | Access admin menu                                                    | false     | 1.3.1   |
| nclaim.bypass.blacklisted_worlds   | Bypass world blacklist restriction                                   | false     | 2.0     |
| nclaim.bypass.blacklisted_regions  | Bypass region blacklist restriction                                  | false     | 2.0     |
| nclaim.bypass.pvp                  | Bypass PvP restrictions in claims                                    | false     | 2.0     |
| nclaim.bypass.interact             | Bypass interaction restrictions in claims                            | false     | 2.0     |
| nclaim.bypass.break                | Bypass block break restrictions in claims                            | false     | 2.0     |
| nclaim.bypass.place                | Bypass block place restrictions in claims                            | false     | 2.0     |
| nclaim.maxclaim.<amount>           | Set the maximum claim count for a player (permission node)           | false     | 2.0     |
| nclaim.use                         | Access basic plugin features (buy a claim, manage claim etc.)        | true      | 2.0     |
| nclaim.level                       | View claim levels and info                                           | false     | 2.0     |
| nclaim.*                           | All plugin permissions                                               | op        | 2.0     |
| nclaim.bypass.max_coop_count       | Bypass maximum number of claim coop player limit                     | false     | 2.1     |
| nclaim.manage_claim_block          | Access open the claim block manager menu                             | false     | 2.1     |
| nclaim.maxcoop.<amount>            | Set the maximum claim coop count for a claim owner (permission node) | false     | 2.1     |
| nclaim.bypass.axsellwand           | Bypass AxSellWand usage restrictions in claims                       | false     | 2.1.4   |
| nclaim.bypass.buy_cooldown           | Bypass claim buy cooldown                       | false     | 3.3.4  |
| nclaim.bypass.claim_nearby_others    | Claim next to other players' claims (ignores `claim_distance`) | false | 4.0 |

---

## 🧩 Placeholders

> Claim placeholders take an optional `_<world>_<chunkX>_<chunkZ>` suffix (world names may contain `_`, e.g. `%nclaim_owner_world_nether_0_0%`). Without it, the chunk the player stands in is used.

**Player**

| Placeholder | Description | Version |
|---|---|---|
| `%nclaim_player_balance%` | Balance (Vault or NClaim's own) | 1.0 |
| `%nclaim_claim_count%` / `%nclaim_claim_count_<player>%` | Number of claims the player owns | 3.2 |
| `%nclaim_coop_count%` / `%nclaim_coop_count_<player>%` | Number of claims the player is a co-op member of | 3.2 |
| `%nclaim_total_value%` | Total value of the player's claims, formatted (`1.5k`) | 4.0 |
| `%nclaim_total_value_raw%` | Same as a plain number, for leaderboard plugins | 4.0 |
| `%nclaim_total_chunks%` | Total chunks of the player's claims | 4.0 |

**Claim**

| Placeholder | Description | Version |
|---|---|---|
| `%nclaim_owner%` | Owner of the claim | 2.1 |
| `%nclaim_display_name%` | Name of the claim | 3.3.4 |
| `%nclaim_expiration%` / `%nclaim_claim_expiry%` | Time left until the claim expires | 1.0 / 3.4.1 |
| `%nclaim_coop_count%` (with location) | Number of co-op members | 1.0 |
| `%nclaim_total_size%` | Number of chunks | 1.0 |
| `%nclaim_claim_main_value%` | Block value of the main chunk | 2.0 |
| `%nclaim_claim_total_value%` | Block value of the whole claim | 2.0 |
| `%nclaim_sale_status%` | Sale text with price, empty when not for sale | 3.3.4 |

**Other**

| Placeholder | Description | Version |
|---|---|---|
| `%nclaim_block_value_<material>%` | Value of a block in `block_levels.yml`, e.g. `diamond_block` | 2.0 |
| `%nclaim_get_<string\|int\|boolean>_<path>%` | A value from `config.yml` | 1.0 |
| `%nclaim_get_list_<path>_<index>%` | An entry of a list in `config.yml` | 1.0 |

> - Claim values are cached and updated when the claim is recalculated (auto-save or `/nclaim level`).
> - `%nclaim_get_...%` never reveals the `database` or `webhook_settings` sections.
> - Leaderboards: with [ajLeaderboards](https://modrinth.com/plugin/ajleaderboards), use `/ajlb add nclaim_total_value_raw`, `nclaim_claim_count` or `nclaim_total_chunks`.

---

## 🤝 Contributing & Issues

Feel free to fork, submit pull requests, or open [issues](https://github.com/aysihuniks/NClaim/issues) for bugs and feature requests.
For questions or suggestions, contact [aysihuniks](https://github.com/aysihuniks).

## 👥 Contributors

Many thanks to everyone who has contributed to this project, including:
- [desaxxx](https://github.com/desaxxx)

## 🤑 Support

[![buy_me_a_coffee](https://github.com/intergrav/devins-badges/blob/v3/assets/compact/donate/buymeacoffee-singular_46h.png)](https://buymeacoffee.com/aysihuniks)

## 📄 License

This project is licensed under the NESOI Plugin License v1.0.

---

## 📸 Screenshots

![2025-06-17_15 36 48](https://github.com/user-attachments/assets/3dbfbaf5-a3a4-4dc5-98a4-45ab483742cd)
![2025-06-17_15 37 52](https://github.com/user-attachments/assets/c5705bba-8e42-4877-b53c-4379fcd379a4)
![1](https://github.com/user-attachments/assets/20e6aeb3-5d69-490f-b67d-cae84d38d02a)
![2](https://github.com/user-attachments/assets/dc3c25c3-87c1-4539-bb88-5ca3fd8b0ce6)
![3](https://github.com/user-attachments/assets/4e5807ce-998f-4121-91c5-a4ceb1d17248)
![4](https://github.com/user-attachments/assets/d87d0166-6d59-449d-9fd7-bbceb55102f5)
![5](https://github.com/user-attachments/assets/6b5067f4-c191-41ae-bfd0-ce8e04151a57)
![6](https://github.com/user-attachments/assets/3ac88a5b-df70-4cc4-be00-215d6eadbf3a)
![7](https://github.com/user-attachments/assets/7456bb35-bab1-4dda-93d6-b926b8790191)
![8](https://github.com/user-attachments/assets/a8770a50-4d63-4d09-a31e-a0d10aed1eec)
