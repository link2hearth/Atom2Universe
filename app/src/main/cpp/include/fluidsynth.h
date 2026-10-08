/* FluidSynth - A Software Synthesizer
 *
 * Copyright (C) 2003  Peter Hanappe and others.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA
 */

#ifndef _FLUIDSYNTH_H
#define _FLUIDSYNTH_H

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

/* FluidSynth return values */
#define FLUID_OK      (0)
#define FLUID_FAILED  (-1)

/* FluidSynth version */
#define FLUIDSYNTH_VERSION_MAJOR 2
#define FLUIDSYNTH_VERSION_MINOR 4
#define FLUIDSYNTH_VERSION_MICRO 0
#define FLUIDSYNTH_VERSION       "2.4.0"

/* FluidSynth API visibility macro */
#if defined(WIN32) || defined(_WIN32)
  #if defined(FLUIDSYNTH_NOT_A_DLL) || defined(FLUIDSYNTH_STATIC)
    #define FLUIDSYNTH_API
  #elif defined(FLUIDSYNTH_DLL_EXPORTS)
    #define FLUIDSYNTH_API __declspec(dllexport)
  #else
    #define FLUIDSYNTH_API __declspec(dllimport)
  #endif
#elif defined(__GNUC__)
  #if defined(FLUIDSYNTH_NOT_A_DLL) || defined(FLUIDSYNTH_STATIC)
    #define FLUIDSYNTH_API
  #else
    #define FLUIDSYNTH_API __attribute__((visibility("default")))
  #endif
#else
  #define FLUIDSYNTH_API
#endif

/* Typedefs for FluidSynth structures */
typedef struct _fluid_settings_t fluid_settings_t;
typedef struct _fluid_synth_t fluid_synth_t;
typedef struct _fluid_sfont_t fluid_sfont_t;
typedef struct _fluid_preset_t fluid_preset_t;
typedef struct _fluid_sample_t fluid_sample_t;
typedef struct _fluid_voice_t fluid_voice_t;
typedef struct _fluid_audio_driver_t fluid_audio_driver_t;
typedef struct _fluid_midi_driver_t fluid_midi_driver_t;
typedef struct _fluid_midi_event_t fluid_midi_event_t;
typedef struct _fluid_midi_router_t fluid_midi_router_t;
typedef struct _fluid_midi_router_rule_t fluid_midi_router_rule_t;
typedef struct _fluid_player_t fluid_player_t;
typedef struct _fluid_sequencer_t fluid_sequencer_t;
typedef struct _fluid_event_t fluid_event_t;
typedef struct _fluid_ladspa_fx_t fluid_ladspa_fx_t;
typedef struct _fluid_file_renderer_t fluid_file_renderer_t;
typedef struct _fluid_cmd_handler_t fluid_cmd_handler_t;
typedef struct _fluid_shell_t fluid_shell_t;
typedef struct _fluid_server_t fluid_server_t;
typedef struct _fluid_hashtable_t fluid_hashtable_t;
typedef struct _fluid_sfloader_t fluid_sfloader_t;

/* Function pointer types */
typedef int (*fluid_audio_func_t)(void *data, int len, int nfx, float **fx, int nout, float **out);

/* Enum for player status */
enum fluid_player_status {
    FLUID_PLAYER_READY = 0,
    FLUID_PLAYER_PLAYING = 1,
    FLUID_PLAYER_STOPPING = 2,
    FLUID_PLAYER_DONE = 3
};

/* ==================== Settings API ==================== */

FLUIDSYNTH_API fluid_settings_t *new_fluid_settings(void);
FLUIDSYNTH_API void delete_fluid_settings(fluid_settings_t *settings);

FLUIDSYNTH_API int fluid_settings_setstr(fluid_settings_t *settings, const char *name, const char *str);
FLUIDSYNTH_API int fluid_settings_setnum(fluid_settings_t *settings, const char *name, double val);
FLUIDSYNTH_API int fluid_settings_setint(fluid_settings_t *settings, const char *name, int val);

FLUIDSYNTH_API int fluid_settings_getstr(fluid_settings_t *settings, const char *name, char **str);
FLUIDSYNTH_API int fluid_settings_getnum(fluid_settings_t *settings, const char *name, double *val);
FLUIDSYNTH_API int fluid_settings_getint(fluid_settings_t *settings, const char *name, int *val);

FLUIDSYNTH_API int fluid_settings_copystr(fluid_settings_t *settings, const char *name, char *str, int len);
FLUIDSYNTH_API int fluid_settings_dupstr(fluid_settings_t *settings, const char *name, char **str);
FLUIDSYNTH_API int fluid_settings_getnum_default(fluid_settings_t *settings, const char *name, double *val);
FLUIDSYNTH_API int fluid_settings_getint_default(fluid_settings_t *settings, const char *name, int *val);
FLUIDSYNTH_API int fluid_settings_getstr_default(fluid_settings_t *settings, const char *name, char **def);
FLUIDSYNTH_API int fluid_settings_getnum_range(fluid_settings_t *settings, const char *name, double *min, double *max);
FLUIDSYNTH_API int fluid_settings_getint_range(fluid_settings_t *settings, const char *name, int *min, int *max);

/* ==================== Synth API ==================== */

FLUIDSYNTH_API fluid_synth_t *new_fluid_synth(fluid_settings_t *settings);
FLUIDSYNTH_API void delete_fluid_synth(fluid_synth_t *synth);
FLUIDSYNTH_API fluid_settings_t *fluid_synth_get_settings(fluid_synth_t *synth);

/* SoundFont loading */
FLUIDSYNTH_API int fluid_synth_sfload(fluid_synth_t *synth, const char *filename, int reset_presets);
FLUIDSYNTH_API int fluid_synth_sfunload(fluid_synth_t *synth, int id, int reset_presets);
FLUIDSYNTH_API int fluid_synth_sfreload(fluid_synth_t *synth, int id);
FLUIDSYNTH_API fluid_sfont_t *fluid_synth_get_sfont(fluid_synth_t *synth, unsigned int num);
FLUIDSYNTH_API fluid_sfont_t *fluid_synth_get_sfont_by_id(fluid_synth_t *synth, int id);
FLUIDSYNTH_API fluid_sfont_t *fluid_synth_get_sfont_by_name(fluid_synth_t *synth, const char *name);
FLUIDSYNTH_API int fluid_synth_sfcount(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_set_bank_offset(fluid_synth_t *synth, int sfont_id, int offset);
FLUIDSYNTH_API int fluid_synth_get_bank_offset(fluid_synth_t *synth, int sfont_id);

/* MIDI channel messages */
FLUIDSYNTH_API int fluid_synth_noteon(fluid_synth_t *synth, int chan, int key, int vel);
FLUIDSYNTH_API int fluid_synth_noteoff(fluid_synth_t *synth, int chan, int key);
FLUIDSYNTH_API int fluid_synth_cc(fluid_synth_t *synth, int chan, int ctrl, int val);
FLUIDSYNTH_API int fluid_synth_get_cc(fluid_synth_t *synth, int chan, int ctrl, int *pval);
FLUIDSYNTH_API int fluid_synth_pitch_bend(fluid_synth_t *synth, int chan, int val);
FLUIDSYNTH_API int fluid_synth_get_pitch_bend(fluid_synth_t *synth, int chan, int *ppitch_bend);
FLUIDSYNTH_API int fluid_synth_pitch_wheel_sens(fluid_synth_t *synth, int chan, int val);
FLUIDSYNTH_API int fluid_synth_get_pitch_wheel_sens(fluid_synth_t *synth, int chan, int *pval);
FLUIDSYNTH_API int fluid_synth_program_change(fluid_synth_t *synth, int chan, int program);
FLUIDSYNTH_API int fluid_synth_channel_pressure(fluid_synth_t *synth, int chan, int val);
FLUIDSYNTH_API int fluid_synth_key_pressure(fluid_synth_t *synth, int chan, int key, int val);
FLUIDSYNTH_API int fluid_synth_bank_select(fluid_synth_t *synth, int chan, int bank);
FLUIDSYNTH_API int fluid_synth_sfont_select(fluid_synth_t *synth, int chan, int sfont_id);
FLUIDSYNTH_API int fluid_synth_program_select(fluid_synth_t *synth, int chan, int sfont_id, int bank_num, int preset_num);
FLUIDSYNTH_API int fluid_synth_program_select_by_sfont_name(fluid_synth_t *synth, int chan, const char *sfont_name, int bank_num, int preset_num);
FLUIDSYNTH_API int fluid_synth_get_program(fluid_synth_t *synth, int chan, int *sfont_id, int *bank_num, int *preset_num);
FLUIDSYNTH_API int fluid_synth_unset_program(fluid_synth_t *synth, int chan);
FLUIDSYNTH_API int fluid_synth_program_reset(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_system_reset(fluid_synth_t *synth);

/* All notes/sounds off */
FLUIDSYNTH_API int fluid_synth_all_notes_off(fluid_synth_t *synth, int chan);
FLUIDSYNTH_API int fluid_synth_all_sounds_off(fluid_synth_t *synth, int chan);

/* Reverb */
FLUIDSYNTH_API int fluid_synth_set_reverb_on(fluid_synth_t *synth, int on);
FLUIDSYNTH_API int fluid_synth_reverb_on(fluid_synth_t *synth, int fx_group, int on);
FLUIDSYNTH_API int fluid_synth_set_reverb(fluid_synth_t *synth, double roomsize, double damping, double width, double level);
FLUIDSYNTH_API int fluid_synth_set_reverb_group_roomsize(fluid_synth_t *synth, int fx_group, double roomsize);
FLUIDSYNTH_API int fluid_synth_set_reverb_group_damp(fluid_synth_t *synth, int fx_group, double damping);
FLUIDSYNTH_API int fluid_synth_set_reverb_group_width(fluid_synth_t *synth, int fx_group, double width);
FLUIDSYNTH_API int fluid_synth_set_reverb_group_level(fluid_synth_t *synth, int fx_group, double level);
FLUIDSYNTH_API double fluid_synth_get_reverb_roomsize(fluid_synth_t *synth);
FLUIDSYNTH_API double fluid_synth_get_reverb_damp(fluid_synth_t *synth);
FLUIDSYNTH_API double fluid_synth_get_reverb_level(fluid_synth_t *synth);
FLUIDSYNTH_API double fluid_synth_get_reverb_width(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_get_reverb_group_roomsize(fluid_synth_t *synth, int fx_group, double *roomsize);
FLUIDSYNTH_API int fluid_synth_get_reverb_group_damp(fluid_synth_t *synth, int fx_group, double *damping);
FLUIDSYNTH_API int fluid_synth_get_reverb_group_width(fluid_synth_t *synth, int fx_group, double *width);
FLUIDSYNTH_API int fluid_synth_get_reverb_group_level(fluid_synth_t *synth, int fx_group, double *level);

/* Chorus */
FLUIDSYNTH_API int fluid_synth_set_chorus_on(fluid_synth_t *synth, int on);
FLUIDSYNTH_API int fluid_synth_chorus_on(fluid_synth_t *synth, int fx_group, int on);
FLUIDSYNTH_API int fluid_synth_set_chorus(fluid_synth_t *synth, int nr, double level, double speed, double depth_ms, int type);
FLUIDSYNTH_API int fluid_synth_set_chorus_group_nr(fluid_synth_t *synth, int fx_group, int nr);
FLUIDSYNTH_API int fluid_synth_set_chorus_group_level(fluid_synth_t *synth, int fx_group, double level);
FLUIDSYNTH_API int fluid_synth_set_chorus_group_speed(fluid_synth_t *synth, int fx_group, double speed);
FLUIDSYNTH_API int fluid_synth_set_chorus_group_depth(fluid_synth_t *synth, int fx_group, double depth_ms);
FLUIDSYNTH_API int fluid_synth_set_chorus_group_type(fluid_synth_t *synth, int fx_group, int type);
FLUIDSYNTH_API int fluid_synth_get_chorus_nr(fluid_synth_t *synth);
FLUIDSYNTH_API double fluid_synth_get_chorus_level(fluid_synth_t *synth);
FLUIDSYNTH_API double fluid_synth_get_chorus_speed(fluid_synth_t *synth);
FLUIDSYNTH_API double fluid_synth_get_chorus_depth(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_get_chorus_type(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_get_chorus_group_nr(fluid_synth_t *synth, int fx_group, int *nr);
FLUIDSYNTH_API int fluid_synth_get_chorus_group_level(fluid_synth_t *synth, int fx_group, double *level);
FLUIDSYNTH_API int fluid_synth_get_chorus_group_speed(fluid_synth_t *synth, int fx_group, double *speed);
FLUIDSYNTH_API int fluid_synth_get_chorus_group_depth(fluid_synth_t *synth, int fx_group, double *depth_ms);
FLUIDSYNTH_API int fluid_synth_get_chorus_group_type(fluid_synth_t *synth, int fx_group, int *type);

/* Gain */
FLUIDSYNTH_API void fluid_synth_set_gain(fluid_synth_t *synth, float gain);
FLUIDSYNTH_API float fluid_synth_get_gain(fluid_synth_t *synth);

/* Polyphony */
FLUIDSYNTH_API int fluid_synth_set_polyphony(fluid_synth_t *synth, int polyphony);
FLUIDSYNTH_API int fluid_synth_get_polyphony(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_get_active_voice_count(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_get_internal_bufsize(fluid_synth_t *synth);

/* Audio synthesis */
FLUIDSYNTH_API int fluid_synth_write_s16(fluid_synth_t *synth, int len, void *lout, int loff, int lincr, void *rout, int roff, int rincr);
FLUIDSYNTH_API int fluid_synth_write_float(fluid_synth_t *synth, int len, void *lout, int loff, int lincr, void *rout, int roff, int rincr);
FLUIDSYNTH_API int fluid_synth_nwrite_float(fluid_synth_t *synth, int len, float **left, float **right, float **fx_left, float **fx_right);
FLUIDSYNTH_API int fluid_synth_process(fluid_synth_t *synth, int len, int nfx, float **fx, int nout, float **out);

/* Tuning */
FLUIDSYNTH_API int fluid_synth_activate_key_tuning(fluid_synth_t *synth, int bank, int prog, const char *name, const double *pitch, int apply);
FLUIDSYNTH_API int fluid_synth_activate_octave_tuning(fluid_synth_t *synth, int bank, int prog, const char *name, const double *pitch, int apply);
FLUIDSYNTH_API int fluid_synth_tune_notes(fluid_synth_t *synth, int bank, int prog, int len, const int *keys, const double *pitch, int apply);
FLUIDSYNTH_API int fluid_synth_activate_tuning(fluid_synth_t *synth, int chan, int bank, int prog, int apply);
FLUIDSYNTH_API int fluid_synth_deactivate_tuning(fluid_synth_t *synth, int chan, int apply);

/* Misc */
FLUIDSYNTH_API double fluid_synth_get_cpu_load(fluid_synth_t *synth);
FLUIDSYNTH_API int fluid_synth_handle_midi_event(fluid_synth_t *synth, fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_synth_start(fluid_synth_t *synth, unsigned int id, fluid_preset_t *preset, int audio_chan, int midi_chan, int key, int vel);
FLUIDSYNTH_API int fluid_synth_stop(fluid_synth_t *synth, unsigned int id);

/* ==================== Audio Driver API ==================== */

FLUIDSYNTH_API fluid_audio_driver_t *new_fluid_audio_driver(fluid_settings_t *settings, fluid_synth_t *synth);
FLUIDSYNTH_API fluid_audio_driver_t *new_fluid_audio_driver2(fluid_settings_t *settings, fluid_audio_func_t func, void *data);
FLUIDSYNTH_API void delete_fluid_audio_driver(fluid_audio_driver_t *driver);
FLUIDSYNTH_API int fluid_audio_driver_register(const char **adrivers);

/* ==================== MIDI Player API ==================== */

FLUIDSYNTH_API fluid_player_t *new_fluid_player(fluid_synth_t *synth);
FLUIDSYNTH_API void delete_fluid_player(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_add(fluid_player_t *player, const char *midifile);
FLUIDSYNTH_API int fluid_player_add_mem(fluid_player_t *player, const void *buffer, size_t len);
FLUIDSYNTH_API int fluid_player_play(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_stop(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_join(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_set_loop(fluid_player_t *player, int loop);
FLUIDSYNTH_API int fluid_player_set_tempo(fluid_player_t *player, int tempo_type, double tempo);
FLUIDSYNTH_API int fluid_player_set_midi_tempo(fluid_player_t *player, int tempo);
FLUIDSYNTH_API int fluid_player_set_bpm(fluid_player_t *player, int bpm);
FLUIDSYNTH_API int fluid_player_get_status(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_get_current_tick(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_get_total_ticks(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_get_bpm(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_get_midi_tempo(fluid_player_t *player);
FLUIDSYNTH_API int fluid_player_seek(fluid_player_t *player, int ticks);
FLUIDSYNTH_API int fluid_player_set_playback_callback(fluid_player_t *player, int (*handler)(void *, fluid_midi_event_t *), void *handler_data);

/* ==================== MIDI Event API ==================== */

FLUIDSYNTH_API fluid_midi_event_t *new_fluid_midi_event(void);
FLUIDSYNTH_API void delete_fluid_midi_event(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_type(fluid_midi_event_t *event, int type);
FLUIDSYNTH_API int fluid_midi_event_get_type(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_channel(fluid_midi_event_t *event, int chan);
FLUIDSYNTH_API int fluid_midi_event_get_channel(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_key(fluid_midi_event_t *event, int key);
FLUIDSYNTH_API int fluid_midi_event_get_key(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_velocity(fluid_midi_event_t *event, int vel);
FLUIDSYNTH_API int fluid_midi_event_get_velocity(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_control(fluid_midi_event_t *event, int ctrl);
FLUIDSYNTH_API int fluid_midi_event_get_control(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_value(fluid_midi_event_t *event, int val);
FLUIDSYNTH_API int fluid_midi_event_get_value(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_program(fluid_midi_event_t *event, int val);
FLUIDSYNTH_API int fluid_midi_event_get_program(fluid_midi_event_t *event);
FLUIDSYNTH_API int fluid_midi_event_set_pitch(fluid_midi_event_t *event, int val);
FLUIDSYNTH_API int fluid_midi_event_get_pitch(fluid_midi_event_t *event);

/* ==================== Version API ==================== */

FLUIDSYNTH_API void fluid_version(int *major, int *minor, int *micro);
FLUIDSYNTH_API char *fluid_version_str(void);

/* ==================== Logging API ==================== */

enum fluid_log_level {
    FLUID_PANIC = 0,
    FLUID_ERR = 1,
    FLUID_WARN = 2,
    FLUID_INFO = 3,
    FLUID_DBG = 4,
    LAST_LOG_LEVEL
};

typedef void (*fluid_log_function_t)(int level, const char *message, void *data);

FLUIDSYNTH_API fluid_log_function_t fluid_set_log_function(int level, fluid_log_function_t fun, void *data);
FLUIDSYNTH_API void fluid_default_log_function(int level, const char *message, void *data);
FLUIDSYNTH_API int fluid_log(int level, const char *fmt, ...);

#ifdef __cplusplus
}
#endif

#endif /* _FLUIDSYNTH_H */
