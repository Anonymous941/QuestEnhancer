# 11: UI sounds (custom charging sound)

Our own Magisk module, after the Discord tip that replacing
`/system/product/media/audio/ui/ChargingStarted.ogg` changes the plug-in sound.
It works for any sound in that folder. Module id `quest1_ui_sounds`.

No sound files ship with it: you bring your own `.ogg`. It must be a real Ogg
file (its first bytes are `OggS`); an mp3 renamed to `.ogg` won't play.

## Use

Put your files in `/sdcard/QuestEnhancer/sounds/` (the install makes the folder), then
in the QuestEnhancer Manager (System > UI sounds), or:

    su -c 'qesound stock'                     # the stock sound names
    su -c 'qesound set ChargingStarted /sdcard/QuestEnhancer/sounds/charging.ogg'
    su -c 'qesound'                           # what is replaced
    su -c 'qesound reset ChargingStarted'     # back to Meta's sound

The module mounts its copies over the stock files at boot, so a change plays after a
restart. `/system` is never written; uninstalling the module is a full revert.

## Status

Not yet on the headset, 2026-11-19.
