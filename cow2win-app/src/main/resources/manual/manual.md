# Cow2Win Manual

This manual describes Cow2Win 1.0.11.

<a name="introduction"></a>

## Introduction

Cow2Win is a desktop tool for the **Clash of Worlds** guild event in *Hero Wars: Dominion Era*.

In Clash of Worlds your guild has **fewer defense slots than teams**. Every member can prepare up
to three hero teams and two titan teams, but the fortifications only have room for some of them.
So someone has to decide which team defends which fortification – and which of the weakest teams
stay out on purpose. Cow2Win helps you make that decision and then shows you, step by step, what
to change in the game.

Cow2Win is meant for whoever sets up the guild's defense: the guild master or the officers who
place the teams. You do not need it every day – **once per season is usually enough**, plus a
quick update whenever your guild changes a lot.

Cow2Win only plans the **defense**. It never changes anything in the game by itself: you enter
your data, Cow2Win suggests a lineup, and you move the teams in the game yourself.

The app guides you through three stages:

1. **Input** – your guild, its members and their teams.
2. **Strategic concept** – plan the lineup: which team goes where.
3. **Output** – the steps to get from the lineup in the game to the new one.

The user interface is available in English, German, French and Spanish (*File* → *Settings …*).
This manual exists in English only. All menu and button names in it are written as they appear
in the **English** interface, in *italics*.

A few words used throughout this manual:

- **Fortification type** – heroes or titans. Hero teams only defend hero fortifications, titan
  teams only titan fortifications. You switch between the two in the context bar.
- **Live** (the *live lineup*) – the lineup as it is currently set up in the game.
- **Lineup** – a plan: which team defends which fortification. You can keep several lineups and
  compare them.
- **CowScore** – Cow2Win's own rating of how well a team fits a fortification, see
  [CowScore](#cowscore).

<a name="getting-started"></a>

## Getting started

### Download and first start

Get the latest Windows version from the
[Releases page](https://github.com/Cow2Win/Cow2Win/releases): unzip it and start `Cow2Win.exe`.
No separate Java installation is needed.

Cow2Win is not code-signed, so on the first start Windows may show "Windows protected your PC"
(Microsoft Defender SmartScreen). Click **More info** and then **Run anyway**. To avoid the warning
altogether: before unzipping, right-click the downloaded zip, choose **Properties**, tick
**Unblock** and click **OK**, then unzip it.

On the first start a **demo guild** shows how everything works: an anonymized real guild with its
live lineup, an optimized lineup to compare it with, and a Clash of Worlds Journal with one example
battle. Look around in it before you create your own guild.

At every start Cow2Win checks whether a newer version is available. If there is one, it offers to
open the release page.

### A tour of the main window

From top to bottom:

- **Menu bar** – *File*, *Guild*, *Journal* (guild masters only, see
  [Clash of Worlds Journal](#journal)) and *Help*.
- **Context bar** – what you select here applies everywhere: the guild (*Guild:*), the
  fortification type (*Heroes* | *Titans*) and the lineup (*Lineup:*), each with its save button.
  A hint appears while something is unsaved. On the right you see how old the live lineup is
  (*Live, as of …*).
- **Process bar** – one tile per stage: *Input*, *Strategic concept*, *Output*. Click a tile to
  switch to that stage. Each tile has a small traffic light and a short text that tell you whether
  the stage needs attention, for example *Teams recorded*, *3 slots free* or *5 steps to apply*.
- **Stage view** – the work area of the active stage. On the left the **action list** with the
  functions of the stage, grouped under small headings; in the middle the work area (member
  overview, fortification map or change plan); on the right an **info panel** with details on
  what you selected.
- **Status bar** – the *Data status* of the open guild, for example how old the teams are.

### Guilds

Cow2Win can manage several guilds – for example your main account and a second account in
another guild. Each guild has its own members, teams, lineups and journal.

- **Create a guild:** *Guild* → *Create new guild …*. Enter the name and tick
  *I am the guild master* if you are – only then the journal is available for this guild. This
  cannot be changed later.
- **Open a guild:** choose it under *Guild:* in the context bar.
- **Remove a guild:** *Guild* → *Remove selected guild*. This deletes the guild folder and
  everything in it, including its journal. The last remaining guild cannot be removed.
- **Guild note:** *Guild* → *Edit guild note …* (also a button next to the guild in the context
  bar) opens a free text note for the guild – for reminders, agreements, anything you like.

### Where your data is stored

Everything lives in one folder, the **workspace** – by default `.cow2Win\workspace` in your user
folder. It holds one folder per guild with its members, teams, lineups, note and journal.

Cow2Win makes **backups** by itself: at the start it saves the whole workspace as
`workspace-daily.zip` once a day and as `workspace-weekly.zip` once a week, in the backup folder
(by default `.cow2Win\backup` in your user folder). You can change both folders, restore a backup
or move the workspace under *File* → *Settings …*, see [Settings and maintenance](#settings).

<a name="input"></a>

## Stage 1: Input

In the input stage you record your guild: its members and their teams. Cow2Win can only plan with
teams it knows, so this is where most of the typing happens – but only the first time. Later you
just keep the power values up to date.

### Members

The work area shows the **member overview**: one row per member with the teams of the selected
fortification type – *Member*, *Teams*, *Total power*, *Strongest team* and *Last modified*. Click
a member to see the teams in the info panel; double-click (or press Enter) to edit the teams of
this member only.

- *Add member …* asks for the name of the new member.
- *Delete member* deletes the selected member after a confirmation. The member's teams and their
  entries in all lineups are removed as well. This cannot be undone.

A guild has at most 30 members. Each member can have up to **3 hero teams** and **2 titan teams**.

*Show outdated only* filters the overview to members whose teams are probably out of date – older
than the number of days set in the settings, or, for a guild master, changed according to the
journal.

### Maintain live lineup

*Maintain live lineup …* is the central place to enter teams. It opens one window for the selected
fortification type with **all teams of the guild** in one table: every team is one row with its
*Fortification*, *Member*, *Power*, *Team*, *Buff*, *Score* and *Last modified*.

The *Fortification* column is where the team stands **in the game right now**. That is why this
window also maintains the **live lineup**: whenever you change a team's fortification here, the
live lineup changes with it. This is the only place where the live lineup can be edited by hand
(the other one is *Apply to live* in the output stage).

Below the table, an editor shows the selected team:

- **Hero teams:** up to five heroes, the team's **power**, the **pet** and the **war flag**. Each
  pet and each war flag can only be used once per member.
- **Titan teams:** up to five titans, the **power** and up to two **totems**. A totem requires at
  least 2 titans of its element in the team.

Power is the value the game shows for the team. Cow2Win rejects values above 2,000,000 as a
probable typo.

You can filter the table by fortification (*All fortifications* or one of them) and search by
member or by a hero or titan name (*Search:*). Problems that would prevent saving – a team without
member, without power, too many teams for a member – and overfull fortifications are highlighted
right away. *Save teams* saves everything; if you close the window with unsaved changes, Cow2Win
asks.

### Fast entry with the keyboard

The fastest way to enter teams is without the mouse. In every selection field the first few
letters are enough – "se" picks Sebastian – and Tab moves to the next field.

- **Ctrl + Plus** – add a new team (the cursor jumps to the member field).
- **Enter** in the member field – take over the member; an unknown name creates a new member.
- **Enter** in the table – jump into the editor to edit the selected team.
- **Del** in the table – delete the selected team (saved right away).
- **Tab / Shift + Tab** – next / previous field.
- **Shift + Enter** – save all teams.
- **F1 – F5** – load template 1 – 5 into the team (in the table and in the editor).
- **Shift + F1 – F5** – in the editor: save the current team as template 1 – 5.

In this window F1 loads a template – it does **not** open this manual.

### Team templates

Many members play the same team compositions. Save such a composition once as one of the five
**templates** (Shift + F1 – F5) and load it into any team with F1 – F5. Then you only have to
adjust the power. When you overwrite a template, Cow2Win shows the old and the new content and
asks first. The templates are shared by all your guilds.

<a name="concept"></a>

## Stage 2: Strategic concept

In the strategic concept you plan the lineup: which team defends which fortification. The work
area is the **fortification map**; on the left the action list with the lineup functions and the
algorithm, on the right the info panel.

### Lineups

A guild can have any number of lineups. The one you work on is chosen under *Lineup:* in the
context bar. The live lineup is one of them, but it is read-only here – it only changes through
*Maintain live lineup …* and *Apply to live*.

- *Create new lineup …* – a new, empty lineup. "Live" and "Original" are reserved names.
- *Copy lineup* – a copy of the open lineup, under the same name with a number in brackets.
- *Clear lineup* – removes all team assignments of the open lineup (after a confirmation).
- *Remove lineup* – deletes the lineup and its file. The live lineup and the last remaining lineup
  cannot be removed.
- **Save** – with the save button next to the lineup in the context bar. Changes are not written
  to disk before you save.

### Running an algorithm

Cow2Win fills a lineup with an **algorithm**. Choose one per fortification type under *Algorithm*
in the action list and click *Run assignment*. Below the choice you see a short description;
*Show explanation* (ⓘ) shows the full explanation with the settings used.

If the lineup already has assignments, Cow2Win asks:

- *Clear and reassign* – remove all assignments of this fortification type first, then distribute
  all teams.
- *Only add* – keep the existing assignments and only distribute the remaining teams.

Algorithms never move a team you have already placed – they only add. So you can place a few teams
by hand first and let the algorithm do the rest with *Only add*.

All algorithms plan the **whole** defense, not only the fortifications the enemy can attack first.
Some background helps to understand them:

- The fortifications form a path to the Citadel. A fortification can only be attacked once one of
  its **prerequisites** has fallen – for example, the three titan bastions open up after the
  Bridge. Cow2Win calls such a group of prerequisites a **gate**.
- The attacking guild gets points for every slot it wins and a **capture bonus** for every
  fortification it takes completely. Empty slots in a fortification the attacker can reach are free
  points for the attacker.
- Many fortifications have a **buff**: for every defending hero with a matching role (or titan
  with a matching element) all defenders there get a little stronger – for example more health in
  the Foundry for every Tank.

The algorithms:

**HODOR** (the default) – "hold the door". HODOR protects the gates first – the Bridge, the three
bastions (only the weakest of them counts, because the attacker takes the weakest), the Heroes'
Bridge and the temples – weighted by the points behind them. Then it protects the capture bonuses,
then every slot. It uses the teams' **power** and the **real fortification buff**, which counts the
matching units of the whole fortification. HODOR takes the strongest teams plus a few **reserve
teams** and keeps swapping teams until the lineup no longer improves. Use it if you are not sure.

**HODOR (CowScore)** – like HODOR, but rates every team by its [CowScore](#cowscore) instead of power
and the real buff. Use it if you have tuned the CowScore marks to your own experience and want them
to decide.

**S.H.I.E.L.D** – forward defense. The Bridge gets the strongest teams, then every fortification up
to the titan gates, then the middle row (with priority for the Heroes' Bridge). Everything behind
the Heroes' Bridge – including the temples and the Citadel – gets the weakest teams. Teams never
change their row; within a row they are placed like HODOR. The weakest teams stay out, there is no
reserve. If you have **fewer teams than slots**, S.H.I.E.L.D fills the front first and leaves the
back empty – the other algorithms may leave slots in front empty instead.

**MAX_BUFF** – gets the biggest buff out of your teams. It first fills the buffed fortifications so
that as many units as possible match their buff, no matter how strong the teams are; strength only
decides on a tie. Then the Bridge and the Heroes' Bridge get the strongest remaining teams, then
every other slot by capture bonus. Use it to see how much buff your guild can reach – a weak but
matching team may end up in a buffed fortification.

**Manual** – no automatic assignment. It is not offered under *Run assignment*, only as the default
algorithm in the settings: for example to let Cow2Win place the heroes while you place all titans
by hand.

You can choose a different algorithm for heroes and for titans. The reserve, the buff weights and
the default algorithms are set under *File* → *Settings …* (section *Algorithms*), see
[Settings and maintenance](#settings).

### Placing teams by hand

Click a fortification on the map to see its teams in the info panel. **Double-click** it to open
the **fortification assignment**: one row per slot, where you can

- *Assign team …* – put a team on a free slot,
- *Move/swap …* – move the team to another fortification, or swap it with a team there if that
  fortification is full,
- *Remove* – take the team out of the fortification.

The *Buff* column shows for each team how many of its heroes or titans match the fortification's
buff.

### The fortification map

The map shows all fortifications of the selected type with their teams. Under *Fortification
values* you choose what each fortification shows:

- *Power* – the total power of its teams.
- *Avg. power per slot* – power divided by the slots, so small and large fortifications can be
  compared.
- *Changes* – what changed since you last saved the lineup.
- *Compare to live* – the difference to the live lineup.

The top row also shows a small **heatmap**: where the power of the fortification type sits, from
very high to low. The info panel shows an overview of the lineup and the comparison with the live
lineup (total power, moved, added and removed teams).

### Team overview

*Show hero teams …* (or *Show titan teams …*) lists all teams of the selected fortification type in
one table – one row per team with its *Power*, *Member*, heroes or titans and *Fortification*,
followed by one column for every fortification. It is the quickest way to see where a team would
fit best.

- **Values per fortification:** under *Displayed value* choose what the fortification columns
  show – the team's *CowScore* at that fortification, or its *Matching role count* (how many of its
  heroes or titans match the fortification's buff).
- **Change the power:** click into the *Power* cell and type the new value. Negative values and
  values above 2,000,000 are rejected. The power belongs to the guild: save it with the save button
  at the top of the window (tooltip *Save guild*).
- **Change the fortification:** choose another fortification in the *Fortification* cell. This
  places the team in the **open lineup**; if the fortification is already full, Cow2Win says so and
  leaves the team where it was. Save the lineup as usual with the save button in the context bar.

Sort the table by a fortification column to find the teams that fit a fortification best.

### Comparing lineups

*Compare lineups …* compares the open lineup with any other lineup of the workspace – also with a
lineup of another guild. A **tornado chart** shows the power and the buff per fortification side by
side. A table of key figures shows for both lineups the *Algorithm*, *Power*, *CowScore*,
*Buff matches*, *Occupied slots* and *Teams left out*, with the difference. This is the quickest
way to see how two algorithms differ for your guild.

<a name="output"></a>

## Stage 3: Output

The output stage turns your plan into work in the game. Cow2Win does not change anything in the
game – you move the teams yourself, and Cow2Win tells you which ones.

### The change plan

The **change plan** is the way from the live lineup to your new lineup (the *target*). By default
the target is the lineup open in the context bar; you can also choose another saved lineup or the
result of an algorithm. Click *Generate instructions* to build the plan.

The plan shows the selected fortification type only. It is grouped into **removals** and
**additions**, per fortification, named by member: first take the listed teams out of their
fortifications in the game, then place the new ones. *Copy plan* copies the plan as text, for
example to post it in your guild chat.

If the target already matches the live lineup, the plan says so – there is nothing to do.

### Checking off and applying to live

While you work through the plan in the game, **check** each entry you have done. A click on a
fortification's heading checks all its entries at once. The checks are saved, so you can stop and
continue later.

When you are done, click *Apply heroes to live* (or *Apply titans to live*, also *Apply to live* in
the action list). Cow2Win takes the **checked** entries into the live lineup. From now on the live
lineup matches the game again, and the next plan starts from there.

If you applied only one fortification type so far, the context bar reminds you: *Heroes not live
yet* or *Titans not live yet*.

### The report

*Generate report …* creates an HTML report of the open lineup: statistics on power and buffs, the
teams per fortification with their composition, pet and war flag, and the most used heroes and
titans. The report window shows it; nothing is saved automatically. With *Save report...* you
choose a folder and save it as an HTML file – for example to share it with your officers.

<a name="cowscore"></a>

## CowScore

The **CowScore** rates how well a team fits one particular fortification. It is Cow2Win's own
value – the game does not know it. The same team can have a different CowScore at every
fortification. Cow2Win uses it in the algorithm *HODOR (CowScore)*, in the team overview, the
fortification assignment, the comparison and the report.

### How it is made up

CowScore = team power ÷ 100,000 × (1 + bonus)

**Power is the most important part.** The bonus is small and mainly decides between teams of
similar power. Bonus for a **hero team**:

- **Role buff** – for every hero whose role matches the fortification's buff. This is the biggest
  bonus.
- **Relation** – once if at least one hero is marked "Positive" for this fortification, minus the
  same if one is marked "Negative".
- **Pet** – if the pet is marked for this fortification.
- **War flag** – if it is marked for this fortification; a smaller fixed bonus just for having one.
- **Combo** – once if all heroes of an active hero combo are in the team.

Bonus for a **titan team**: element buff (for every titan whose element matches the buff),
relation, a bonus per **totem**, combo, and a fixed bonus per **super titan**.

A hero or titan that already counts through the buff does not count again through its mark.

With the default settings the role and element buff give 1.5 % per matching unit, the other
bonuses 1.25 % each, an unmarked war flag and each super titan 0.6 %. So a hero team gets at most
12.5 % with the default settings – power always comes first.

### The CowScore settings

*File* → *CowScore settings …* opens the dialog to adjust the CowScore. Its first tab, *Info*,
explains the CowScore with an example and the values currently set. The other tabs:

- *Heroes* and *Titans* – per hero or titan and fortification a rating: *Neutral*, *Positive* or
  *Negative*. *Positive (buff)* is set automatically where the role or element matches the buff
  and cannot be changed.
- *Pets* and *War flags* – mark the fortifications where a pet or war flag is especially useful.
- *Hero combos* and *Titan combos* – heroes or titans that are strong together. Shipped combos
  can be deactivated, your own ones also deleted.

*Restore default values* resets a tab to the shipped values. Changes apply after you save. The
percentages of the bonuses are set under *File* → *Settings …* (*CowScore bonuses*); changed
values mark existing lineups as "to recalculate".

### Do I need it?

No. The default algorithm HODOR does not use the CowScore at all. If you never open the CowScore
settings, Cow2Win works with the shipped values, and you can ignore the CowScore columns. It
becomes useful once you want your own experience – this hero is great in the Foundry, that combo
always holds – to influence the lineup.

<a name="journal"></a>

## Clash of Worlds Journal

The journal collects the **battle logs** of your Clash of Worlds battles. Only a **guild master**
can export these logs in the game, so the *Journal* menu only appears for a guild created with
*I am the guild master*. Cow2Win works exactly the same without the journal – it is an extra.

What the journal does for you:

- keeps every battle with its fortifications and single fights,
- replays a battle step by step on a fortification map,
- keeps your teams' power up to date from the newest defense log,
- builds missing teams from the logs.

**Only the defense log changes your guild.** Cow2Win plans the defense, so only the defense log –
where your members are the defenders – is used to match players and update teams. The attack log
is stored and shown, but never changes anything in your guild.

### Importing battle logs

1. In the game, export the logs on the Clash of Worlds journal page. You get one CSV file for the
   attack and one for the defense of a battle.
2. In Cow2Win open *Journal* → *Battle list …* and click *Import …* – or simply drop the CSV
   files onto the list.
3. A short wizard guides you through the import: *Overview*, *Guild* (the first time: link the game
   guild to your Cow2Win guild), *Players*, *Unknown names* and *Summary*.

You can import a battle while it is still running; a later export of the same battle replaces the
earlier state. Importing the same file again changes nothing. The second file of a battle can be
added later.

### Battle list and details

The battle list shows every battle with date, opponent, result and points. Filter it by *Season:*,
*Result:* and *Opponent:*; the season is determined from the battle date. The sums below always
refer to the battles shown.

*Details …* (or a double-click) opens a battle:

- *General* – date, guilds, result, ranking points and the logs.
- *Fortifications* – per fortification the positions, single fights, wins, losses and points.
- *Attack (visual)* and *Defense (visual)* – the battle step by step on a fortification map, in the
  order of the battle log: which position was attacked and how it went.

*Delete battle …* removes a battle from the journal.

### Update teams (sync)

*Journal* → *Update teams (sync) …* takes over the team power – and, if the defense log contains
them, heroes or titans with pet or totems – from the newest defense log. Logs and stored teams are
matched by the same units or by power (within 3 % "sure", within 10 % "unsure"). You see every
change first and choose which ones to apply. No teams or members are created; war flags and lineups
stay unchanged. Save the guild afterwards.

### Build teams from logs

*Journal* → *Build teams from logs …* creates teams that are missing in Cow2Win from the defense
logs – only teams whose heroes or titans are known from the log. Existing teams are never changed;
only missing teams are created and empty teams filled. *Apply and save* saves the guild. Teams the
logs do not show completely still have to be entered under *Maintain live lineup …*.

### Players and name mappings

The logs only contain the **player names** from the game. The import matches them to your members:
exact or almost identical names automatically, everything else with your help. For a player you can
choose *Assign to …*, *Renamed – was …* (only the member's name changes, teams and lineups stay),
*Add new member*, *Not in Cow2Win*, *Former member* or *Leave open*.

*Journal* → *Players …* shows all players from the logs with their status and lets you correct an
assignment later. It also points out members who did not appear in the recent defense logs –
maybe a typo, a name change, or they left the guild. From there, *Name mappings …* shows how
unknown hero, titan, pet or fortification names from logs were mapped; changes apply to future
imports.

<a name="settings"></a>

## Settings and maintenance

### The settings

*File* → *Settings …*:

- *Language:* – the language of the interface (the manual stays English). Restart Cow2Win for the
  change to take full effect.
- *Default hero algorithm:* and *Default titan algorithm:* – preselected in the strategic concept.
- *Workspace directory:* and *Backup directory:* – where your data and the backups are stored.
- *Restore workspace from backup* – replaces the whole workspace with a daily or weekly backup.
  The current state is saved in the backup folder first. Cow2Win closes for this; the restore runs
  on the next start.
- *Move workspace* – moves the workspace to a new, empty folder. Cow2Win closes; the move runs on
  the next start.
- *Mark teams as outdated by age* – after how many days teams count as outdated in the data
  status.
- *CowScore bonuses* – the percentages of the CowScore bonuses, see [CowScore](#cowscore).
- *Algorithms* – *Reserve teams in the pool* (for HODOR, HODOR (CowScore) and MAX_BUFF) and the
  *Weight per buff effect* (for HODOR and S.H.I.E.L.D): how much each kind of buff counts; 0 means
  the buff is ignored. *Defaults* resets them.

### Update game data

New heroes and titans appear in the game all the time. You do **not** need a new Cow2Win version
for them: *File* → *Update game data …* downloads the latest game data – heroes, titans, pets, war
flags, fortifications, their names and pictures – checks it and shows what is new. If you take it,
it is used **from the next start** of Cow2Win, so restart the program afterwards.

Your guilds, lineups and CowScore settings stay as they are. New heroes and titans start with
**neutral** CowScore values; adjust them in the CowScore settings if you like.

If the new game data needs a newer version of Cow2Win, the dialog says so and offers to open the
releases page.

### New program versions

At every start Cow2Win checks whether a newer version is available and offers to open the release
page. To update, download the new zip, unzip it and start the new `Cow2Win.exe`. Your data stays in
the workspace and is not touched by an update.

### Show Log

*Help* → *Show Log …* shows the log of the open guild: what was done with it and when – guild and
lineups saved, algorithms run, changes applied to live, battles imported. Technical details for
error reports are written to the file `cow2win.log` in the workspace.

### Open Hero Wars

*File* → *Open Hero Wars* opens the Hero Wars website in your browser.

<a name="faq"></a>

## FAQ

### Why are some of my teams left out?

Because the game has fewer defense slots than your guild has teams – with the current game data 55
hero slots and 40 titan slots, while a full guild can prepare 90 hero and 60 titan teams. The
algorithms leave the weakest teams out on purpose. HODOR and MAX_BUFF additionally keep a few
reserve teams in reach while they search. See [Stage 2: Strategic concept](#concept).

### What is the CowScore – and do I need it?

The CowScore is Cow2Win's own rating of how well a team fits a fortification: mainly the team's
power, plus a small bonus for matching buffs, marks, pets, war flags, totems and combos. You do not
need it – the default algorithm HODOR works with power and the real buff. See [CowScore](#cowscore).

### Which algorithm should I use?

Start with **HODOR**, the default. Then try another one, save the result as a second lineup and
use *Compare lineups …* to see the difference. S.H.I.E.L.D suits guilds with fewer teams than
slots, MAX_BUFF shows how much buff you can reach. See [Running an algorithm](#running-an-algorithm).

### Why don't I see the Journal menu?

The journal is only available for guild masters, because only they can export the battle logs in
the game. It appears for a guild created with *I am the guild master*. This choice cannot be
changed later for an existing guild. See [Clash of Worlds Journal](#journal).

### A new hero or titan is missing – what can I do?

Use *File* → *Update game data …* and restart Cow2Win. If the new hero or titan is not in the
update yet, it has not been added to the game data so far – please report it (see the last
question). See [Update game data](#update-game-data).

### Windows says "Windows protected your PC" – is Cow2Win safe?

The warning appears because Cow2Win is not code-signed, not because something was found. Click
**More info** and **Run anyway**, or unblock the zip before unzipping. Cow2Win is open source; you
can look at the code on GitHub. See [Getting started](#getting-started).

### Where are my data stored, and how do I back them up or move them to another PC?

In the workspace folder, by default `.cow2Win\workspace` in your user folder. Cow2Win makes a daily
and a weekly backup by itself. To move to another PC, copy the workspace folder there and select
it under *File* → *Settings …* → *Workspace directory:*. See
[Where your data is stored](#where-your-data-is-stored).

### How do I get my changes into the game?

Cow2Win changes nothing in the game. Generate the change plan in the output stage, move the teams
in the game yourself, check off what you did and click *Apply to live*. See
[Stage 3: Output](#output).

### Does Cow2Win plan the attack too?

No. Cow2Win plans the **defense** only. The journal stores attack logs and shows them, but there is
no attack planning.

### How often should I run it?

Once per season is usually enough – plus whenever many members got noticeably stronger or the
guild changed a lot. Keeping the power values current is quickest with the journal sync (guild
masters) or the keyboard in *Maintain live lineup …*.

### I found a bug / have an idea – where do I report it?

Please open an issue on [GitHub](https://github.com/Cow2Win/Cow2Win/issues) or write to
[cow2win@gmail.com](mailto:cow2win@gmail.com). For a bug, the file `cow2win.log` from your workspace
helps a lot.

<a name="about"></a>

## About

Cow2Win is a fan project and not affiliated with the game's publisher. Hero Wars and its artwork
belong to their owner.

- **Version:** this manual describes Cow2Win 1.0.11. *Help* → *About Cow2Win …* shows the version
  you are running.
- **License:** MIT License – see the
  [LICENSE](https://github.com/Cow2Win/Cow2Win/blob/master/LICENSE) file.
- **Project:** [github.com/Cow2Win/Cow2Win](https://github.com/Cow2Win/Cow2Win)
- **Contact:** [cow2win@gmail.com](mailto:cow2win@gmail.com)

*Help* → *About Cow2Win …* also names the icons and the look and feel Cow2Win uses.
