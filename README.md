# Cow2Win

[![CI](https://github.com/Cow2Win/Cow2Win/actions/workflows/ci.yml/badge.svg)](https://github.com/Cow2Win/Cow2Win/actions/workflows/ci.yml)

A desktop tool for the **Clash of Worlds** guild event in *Hero Wars: Dominion Era*.

In Clash of Worlds a guild has fewer defense slots than teams. Cow2Win helps
you find the strongest defense lineup: it decides which team defends which
fortification and which of the weakest teams are left out. It then shows
you, step by step, what to change in the game.

The app guides you through three steps: **Input** (guild and teams) →
**Strategic concept** (plan the lineup) → **Output** (apply it in the game).

## Features

- **Guild and teams** – members with their hero teams (heroes, pet, war flag)
  and titan teams (titans, totems), with templates for quick entry.
- **Automatic lineup** – several strategies for heroes and titans, from
  "best possible" to "balanced defense". Teams can also be assigned or moved
  by hand.
- **CowScore** – rates how well a team fits a fortification. Adjustable in
  the settings.
- **Fortification map** – power and buff of every fortification at a glance.
  Compare and copy lineups.
- **Change plan** – the steps to get from the live in-game lineup to the new
  one, plus an HTML report.
- **Clash of Worlds Journal** (guild masters only) – import the battle logs
  exported in the game, browse past battles, replay them step by step and
  update your teams from them.
- German, English and French; automatic backups; update check.
- **New heroes and titans without a new version** – *File* → *Update game data …*
  downloads the latest game data (heroes, titans, pets, war flags,
  fortifications, their names and pictures), checks it and uses it from the
  next start on. Your guilds, lineups and CowScore settings stay as they are.

## Download

Get the latest Windows version from
[Releases](https://github.com/Cow2Win/Cow2Win/releases): unzip it and start
`Cow2Win.exe`. No separate Java installation is needed. On the first start a
demo guild shows how everything works: an anonymized real guild with its live
lineup, an optimized lineup to compare it with, and a Clash of Worlds Journal
with one example battle.

### Windows SmartScreen warning

Cow2Win is not code-signed, so on the first start Windows may show
"Windows protected your PC" (Microsoft Defender SmartScreen). Click
**More info** and then **Run anyway**.

To avoid the warning altogether: before unzipping, right-click the downloaded
zip, choose **Properties**, tick **Unblock** and click **OK**, then unzip it.
