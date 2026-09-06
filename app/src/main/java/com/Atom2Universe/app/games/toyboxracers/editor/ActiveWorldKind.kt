package com.Atom2Universe.app.games.toyboxracers.editor

/** Un seul monde est jamais actif à la fois : soit un circuit classique
 * procédural (piste en ruban), soit un monde bâti dans l'éditeur de blocs.
 * Les deux ne doivent jamais être rendus ni simulés en même temps. */
internal enum class ActiveWorldKind { LEGACY, CUSTOM }
