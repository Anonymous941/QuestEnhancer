# Credits

QuestEnhancer stands on two communities: FreeXR, whose tools unlock and root a
Quest 1 in the first place, and the Monterey Club, whose methods fill the Manager.
Thank you to everyone below.

## FreeXR: unlocking and root

Nothing in this repo runs without these. QuestEnhancer ships none of their code;
the guides point to them.

| Who | What |
| --- | --- |
| starseed | [QuestStack](https://github.com/starseed12345/QuestStack), the Quest 1 bootloader unlock tool |
| darknight1050 | [quest-bootloader-unlocker](https://github.com/darknight1050/quest-bootloader-unlocker), the original Quest ABL exploit QuestStack builds on; ARB fuse research; the Quest firmware archive |
| Lumince | [Singularity-Magisk](https://github.com/Lumince/singularity-Magisk) (root on the Quest) and the [Singularity](https://github.com/Lumince/singularity) app |
| XirXes | The Oculus token method used for PrivateQuest setup |
| Lexie | Private Quest setup guide ([FreeXR guides](https://github.com/FreeXR)) |
| ender9492 | Userdebug dumps and rescue images for Quest models |
| ARDiva | The documented table of known Oculus preferences |
| ilovecats | system.db / user0.db and NUX state research |
| xAstroBoy | Oculus preferences research, dogfood and debug panel tools, `oculus_pref_shell` |
| geokar | QuestUnlock (Build.TYPE / dogfood hub module) and a prefs editor app |
| Mandi | Runs the FreeXR community |

And [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk), which
Singularity-Magisk forks.

## Monterey Club: methods

Most of what QuestEnhancer's Manager offers started as a post in the Monterey
Club Discord's methods channel. This table lists each one: who posted it, the
upstream project and its licence, and what this repo does with it. "Linked"
means we point to it and ship none of its code. "Idea" means we wrote our own
code after reading the post, because the upstream has no licence (or none was
posted). Discord names are as they appear on the posts.

| Post | Posted by | Upstream (licence) | In QuestEnhancer |
| --- | --- | --- | --- |
| [v50] QuestEnhancer: unlock hidden settings, remove restrictions | Anonymous950 | [Anonymous941/QuestEnhancer](https://github.com/Anonymous941/QuestEnhancer) (GPL-3.0) | This repo is its fork: the app and its hooks |
| Edit pinned apps | Anonymous950 | post only | Dock hooks (10 pins, no default pins); Manager pins page planned |
| [v50] Magisk module to fix stuff when accountless | Lucy | [LucentW/systemux-accountless](https://github.com/LucentW/systemux-accountless) (no licence) | Fix 04 fetches it (not copied); its dock/Library ideas are our own hooks |
| Meta boot logo changer | ethan52 | post | Fix 05 (our own boot image method) |
| LED control stuff | Eclipse | colours.sh (post) | Idea: fix 09's effects |
| Rainbow led | Uzi (made by mlsplays) | [mlsplays/Quest-root-rainbow-LED-Magisk](https://github.com/mlsplays/Quest-root-rainbow-LED-Magisk) (no licence) | Idea: fix 09's rainbow and worn check |
| Police led | Uzi (made by bobbyjhonnu-afk) | [bobbyjhonnu-afk/Police-led](https://github.com/bobbyjhonnu-afk/Police-led) (no licence) | Idea: fix 09's police effect |
| Trans LED | Jolly ProtoGPT | [ProtogenDev/Quest-root-trans-LED-Magisk](https://github.com/ProtogenDev/Quest-root-trans-LED-Magisk) (no licence) | Not used |
| Status LED module | Juspertinry | [Juspertinry/q1-modules](https://github.com/Juspertinry/q1-modules) q1_led (MIT) | Fix 09 builds on it |
| Passthrough tint colorizer [v50 only] | Juspertinry | q1-modules q1_ptcolor (MIT) | Fix 10, unchanged |
| Passthru tint colorizer for Quest 2 | Uzi, for DesertPrinter | repack | Not used: Quest 2 only, unsafe on a Quest 1 |
| Meta Quest charge sound changer | Uzi | post | Idea: fix 11 (`qesound`) |
| lower brightness to 1! | Uzi | post | Idea: fix 12 (`qebacklight`) |
| Quest Force Max Brightness | Lexie | module (post) | Idea: fix 12 |
| Quest 1 steamlink patcher | Uzi, credits to Mister Anderson | [misteranderson91/quest1-steamlink-patcher](https://github.com/misteranderson91/quest1-steamlink-patcher) (MIT) | Fix 13 is its USB module, unchanged; the patcher is linked |
| Quest Disable Microphone Processing | Lexie | module (post) | Planned: audio module |
| System-wide audio effects patch | Juspertinry | q1-modules q1_audiofx (MIT) | Planned: audio module |
| Pair controllers using ADB | Uzi | post | Manager: Controllers page, a Pair button per hand |
| Factory setup skip (bypass NUX) | Juspertinry | post | Fix 14 (script, for a freshly reset headset) |
| Activate hand tracking without login | rhkdnjs | post | Planned: Manager switch |
| 90hz mod [v50 only] | Juspertinry | q1-modules q1_90hz (MIT) | Linked: needs a patched boot image; Manager shows your panel (Samsung or AUO) for its `--panel` option |
| Controller pose injection (experimental) | snoopythedog | [xhilks92-ctrl/quest-tracking-injection](https://github.com/xhilks92-ctrl/quest-tracking-injection) (MIT) | Linked only: a PC developer tool for firmware 49845030443200410; its README reports earlier view freezes |
| Quest 2 (Hollywood) v50/v56 Hand Tracking on Quest 1 | Bluedragon | Discord attachments (no licence; engine is Meta's) | Manager: Controllers page status and remove-all button; modules not shipped |
| QuestLHSync Quest 1 Support | Skye | [SkyeCA/QuestLHSync](https://github.com/SkyeCA/QuestLHSync) (MIT, from CreoleVR) | Linked for now: needs its PC SteamVR driver |
| Quest 2 controllers on Quest 1 [v50 only] | Juspertinry | q2ctrl-module (no licence) | Linked only: firmware risk |
| Oled2lcd | Uzi | [symbuzzer/oled2lcd](https://github.com/symbuzzer/oled2lcd) (GPL-3.0) | Not yet |
| Custom System Dialog Cubemap | playsamay4 | module (no licence) | Not yet |
| Userdata Backup Tool | playsamay4 | [playsamay4/QuestUserdataBackupTool](https://github.com/playsamay4/QuestUserdataBackupTool) (no licence) | Not yet |
| Headless (no screen) Quest 1 patch [v50 only] | Juspertinry | modules (post) | Not yet |
| v50 SystemUX downgrade (v50 -> v40) | Eclipse | ships Meta's APKs | Linked only |
| How to fix universal menu/passthru not showing | Uzi | post (firmware) | Linked only |
| Orangefox Recovery + 90hz, parts 1-3 | TheCez | post (no licence) | Linked only, unverified |
| Ramblings on using Private Quest for setup | Lexie | [FreeXR guides](https://github.com/FreeXR) (no licence) | Linked only |
| questie | niko | [niko-tar-gz/questie](https://github.com/niko-tar-gz/questie) (GPL-3.0) | Linked only |

People behind the posts and projects above, Monterey Club and upstream:
Anonymous950 (and Anonymous941's original QuestEnhancer), Lucy (LucentW),
ethan52, Eclipse, Uzi, mlsplays, bobbyjhonnu-afk, Jolly ProtoGPT (ProtogenDev),
Juspertinry, Lexie, sctanf, misteranderson91 (Mister Anderson), rhkdnjs,
snoopythedog (xhilks92-ctrl), Bluedragon, Skye (SkyeCA) and CreoleVR,
playsamay4, TheCez, niko and symbuzzer.

Licences above come from each repository's LICENSE file where we cloned it;
"post" means the method was shared as files or commands in Discord with no
repository. If you're an author and want a credit changed, open an issue.
