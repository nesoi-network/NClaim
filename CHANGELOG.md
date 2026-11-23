# Changelog

## 4.1.0-beta.3

### New

- **PvPManager support:** claims with PvP off count as safe zones for PvPManager 4.x. Combat tagged players trying
  to enter them are stopped by PvPManager's anti border hopping (push back, or barriers on its premium version),
  using PvPManager's own settings. Claims with PvP on can still be entered.
- PvP cannot be turned off in a claim while the owner, or a player standing in that claim, is in combat.

## 4.1.0-beta.2

### New

- **Custom menu items:** `material` in `guis.yml` also accepts `itemsadder:namespace:id`, `nexo:id`, `oraxen:id` and
  `model:namespace:key` (resource pack item models, 1.21.4+), next to `STONE`, `PAPER:1001` (custom model data)
  and `HEAD-<texture>`. None of these plugins is required.

## 4.1.0-beta.1

### New

- **Hologram lines:** `hologram_settings.lines` in `config.yml` sets which lines the hologram shows and in what order.
  Built-in lines (`title`, `owner`, `time_left`, `coop_count`, `total_size`, `value`, `edit`) use the texts from
  the language file; any other line is shown as written, with `{owner}`, `{value}`, `{time_left}` and the other
  claim values, colour codes and PlaceholderAPI placeholders.
- **Hologram look:** `height`, `scale`, `background` and `shadow` in `hologram_settings`.
- **`value` hologram line** showing the claim value.

### Changed

- The `show_title`, `show_owner` ... switches are replaced by the line list. Your choices are moved to
  `hologram_settings.lines` on the first start, nothing to do by hand.
- Servers on a beta are told when the final release is out.

## 4.0.1

### New

- **Farmer support:** with [Farmer](https://github.com/Geik-xyz/Farmer-v6) installed, each claim can have one
  farmer. The claim owner owns it, co-ops are added and removed as farmer users, and the farmer is deleted
  with the claim. Add your claim worlds to `allowed-worlds` in Farmer's `config.yml`.

## 4.0.0

A rewrite of the plugin's core for modern Paper and Folia, with redesigned menus.
Existing claims, players and databases from 3.x are loaded as they are.

### ⚠️ Before you update

- **Server:** Paper or Folia **1.21.4 or newer**, **Java 21**. Spigot and older Minecraft versions are
  no longer supported; stay on NClaim 3.4.1 for those.
- **Remove DecentHolograms / FancyHolograms** if you only had them for NClaim: holograms are now built in
  (vanilla text displays). PlaceholderAPI is now optional.
- **Make a copy of `plugins/NClaim/`** before the first start, as with any major update.

### What happens on the first start

| File | What 4.0.0 does |
|------|-----------------|
| `claims.yml`, `players/`, database | Read as-is, nothing to convert. Claims in worlds with `_` in the name (`world_nether`, `world_the_end`) that 3.x failed to load are recovered. |
| `config.yml` | **Kept.** New settings are added with their defaults, your values are untouched. |
| `guis.yml` | **Replaced** (menus were redesigned). Your old file is saved to `backups/`. Re-apply custom texts or materials by hand if you had any. |
| `lang/<your language>.yml` | **Replaced** (messages were rewritten). Your old file is saved to `backups/`. |
| `claim_blocks.yml`, `block_levels.yml` | Kept. |

After 4.0.0, updates go back to only adding new entries to `guis.yml` and the language files.

### New

- **Claim management menu** redesigned: claim info (owner, size, co-ops, time left, value and its most
  valuable blocks), rename, sell / cancel sale, border toggle, all on one screen.
- **Claim blocks**
  - Admins can add claim block types in game: pick any solid block from a searchable list, set a name and a
    price, and offer it to every claim or to one claim only.
  - Admins can re-price (right click) or remove (shift + right click, with confirmation) a block type.
    Claims using a removed block fall back to the default block.
  - The default claim block is always listed, so players can switch back for free.
  - Container claim blocks (shulker boxes, barrels ...) no longer open when clicked.
- **New claim settings** (all off by default, i.e. protected): fire spread, mob griefing (endermen,
  ravagers, withers, door breaking), crop trampling, other explosions (withers, ghast fireballs, beds,
  respawn anchors). The settings menu also has a "reset to defaults" button.
- **Co-op permissions:** each category shows how many permissions are allowed; right click toggles a whole
  category without leaving the overview.
- **Claim borders:** see-through coloured walls instead of particles (green yours, blue co-op, red others,
  yellow free), toggled from the management menu or with `/nclaim border`.
- **Commands:** `/nclaim border`; `/nclaim admin reload` also works from the console.
- **Number format:** prices, balances and values are shown as `1.5k`, `20.3k`, `1.25M` (or in full, see
  `number_format` in `config.yml`). No more `2352.3523536436` in holograms.
- **Name blacklist:** `name_blacklist.txt` blocks offensive claim names, including look-alike spellings.
- **Admin claim list:** a statistics overview, and an empty state that
  says what to do when nobody has a claim yet.
- **Placeholders:** `%nclaim_total_value%`, `%nclaim_total_value_raw%` (plain number for leaderboard plugins such
  as ajLeaderboards) and `%nclaim_total_chunks%`. Claim placeholders now also read worlds with `_` in the name.
- **Permission:** `nclaim.bypass.claim_nearby_others` ignores `claim_distance`.
- **Database:** turning `database.enable` on with an empty database copies the YAML data into it.

### Changed

- Menus grow from 5 to 6 rows only when they need a second row, and page with arrows after that.
- Confirmation menus explain what will happen instead of "click the green dye".
- All disk and database work runs off the main thread; every world access runs on the right Folia region.
- The unused `USE_REDSTONE` co-op permission was removed (it never did anything; redstone parts have their
  own permissions).

### Fixed

- Claims in the nether and the end not loading.
- Claim values with many decimals in holograms and menus.
- Shulker boxes and other containers opening when used as a claim block.

### Notes for server owners

- Operators have every `nclaim.bypass.*` permission, so they are not charged for claims or expansions and
  can open every chest. Test as a normal player to see what players see.
- The maximum number of claims can be set per group with `nclaim.maxclaim.<amount>`.
- Water, ice and stained glass behind a border wall may look invisible on "Fast" and "Fancy" graphics. This is
  how the Minecraft client draws see-through entities and cannot be changed by a plugin; "Fabulous!" graphics
  should show them correctly.
