WeaponsMod Addon — Fresh Animations compatibility pack
=====================================================

Problem
-------
FA+ Player (via Entity Model Features) rewrites player poses after
weaponsmodaddon applies its Blockbench clips in HumanoidModel.setupAnim.
Without this pack: left arm / head stay on FA idle-walk, right arm only
partially blends (often looks tilted), and FA body.ry can wipe aim waist yaw.
Crawl/swim also waves body.rx, which made “clean” arm pivots orbit (arms flail).

What this pack does
-------------------
1) a_player_variables.jpm
   - Holding WeaponMod guns / javelin / scoped muskets sets BOTH Raction and
     Laction (same pattern as bows) so FA defers both arms.
   - Captures ModelPart head/arms/body into var.wm_* after addon setupAnim.
   - varb.wm_upper = guns/javelin OR FA spear holds.
   - varb.wm_crawl_lock = two-handed guns only (musket/blunder/mortar/scoped)
     && (is_crawling || is_swimming). Not flintlock/javelin/empty hands.

2) player.jem / player_slim.jem
   - While wm_upper (third person): head + arm ROTATIONS = var.wm_*.
   - While wm_crawl_lock: arm TRANSLATIONS = var.wm_*arm_t* (mixin shoulders);
     body.rx/rz = vanilla/mixin (blocks FA swim/prone torso wave).
   - Else while wm_upper: arm+head TRANSLATIONS = clean body attach (no FA
     idle/mvmnt/udrwtr/raction ModelPart blend).
   - While is_using_item && wm_upper: body.ry = Addon aim waist.
   - Legs stay on FA.
   - First-person hand layer still uses FA fp_* expressions.

Enable order (top = highest priority)
-------------------------------------
1. WeaponsModAddon-FA-Compat   <-- this pack (must be ABOVE FA+ Player)
2. FA+ Player / Fresh Animations / other FA packs
3. ...

Reference stock: FA+Player-v1.1.zip (also under WeaponsMod/resourcepacks/).

Does NOT contain Blockbench keyframes — those live in the weaponsmodaddon jar.
