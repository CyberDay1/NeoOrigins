tame_mob aggro + defend repro pack
==================================

Backlog item #6 (v2.1.6). Verifies the fix for broken aggro and defend
target goals on the neoorigins:tame_mob power.

Setup
-----
1. Drop this folder under <world>/datapacks/  (or originpacks/ for global).
2. /reload  (or restart world).
3. /origin set test_tame:origin <player> test_tame:tamer

Manual verification steps
-------------------------

Test A — AGGRO (pet targets what the owner attacks)
  1. Pick the "Tame Mob Tester" origin.
  2. /summon minecraft:zombie ~ ~ ~5
  3. Activate tame_mob on the zombie (default keybind, point at it).
     - Sound + happy-villager particles confirm tame succeeded.
  4. /summon minecraft:zombie ~5 ~ ~      (a second zombie, NOT tamed)
  5. Hit the second zombie ONCE with a sword.
  EXPECTED: tamed zombie immediately turns and attacks the second zombie.
  BEFORE FIX: tamed zombie did nothing — aggro goal was missing entirely.

Test B — DEFEND (pet targets what attacked the owner)
  1. Same setup as Test A — one tamed zombie following you.
  2. /summon minecraft:skeleton ~ ~ ~10   (far away, OUT of tamed-mob
     follow distance — this matters; old code used a spatial gate)
  3. Move toward the skeleton until it shoots you ONCE.
  4. Immediately back off so the tamed zombie is closer to you than to
     the skeleton.
  EXPECTED: tamed zombie aggros the skeleton and pathfinds toward it.
  BEFORE FIX: tamed zombie ignored the skeleton because the old
  NearestAttackableTargetGoal predicate only fired when the attacker
  was already inside the mob's own search box.

Test C — OWNER FORGIVENESS (regression guard)
  1. Hit your own tamed mob with a sword.
  EXPECTED: tamed mob does NOT retaliate against you. It clears its
  last-hurt-by reference each tick the attacker is the owner.

Test D — CONTROL (regression guard)
  1. /origin set test_tame:origin <player> test_tame:control
  2. Attempt to activate tame_mob — power should not exist for this
     origin (no keybind / no effect). Confirms power is opt-in.

Notes
-----
- hostile_only is set to false in this pack so you can tame any mob
  (including passive ones) for easier testing. The shipped power
  defaults to hostile_only=true.
- death_damage is 0 here so you don't take backlash damage when the
  test mobs die.
- cooldown_ticks is short (40t = 2s) for fast iteration.
