#ifndef __CONFIG_H__
#define __CONFIG_H__

#include <stdint.h>
#include "stm32f4xx_hal.h"

/* ---------------------------------------------------------------
 * Single-word persistent storage
 * ---------------------------------------------------------------
 * We keep one 32-bit value (the moisture-threshold) at the very
 * start of Flash sector 11.  STM32F407 with 1 MiB Flash:
 *   sector 11 base = 0x080E0000
 * If you use a smaller part, pick the last sector available.
 */
#define CFG_FLASH_SECTOR     FLASH_SECTOR_11
#define CFG_FLASH_ADDRESS    ((uint32_t)0x080E0000u)

/* ---------- preferred (new) API -------------------------------- */
void     cfg_store_uint32 (uint32_t val);      /* write if changed   */
uint32_t cfg_load_uint32  (uint32_t *dest);    /* read current value */

/* ---------- legacy wrappers (older code still calls these) ------ */
static inline void save_threshold_to_flash(uint32_t v) { cfg_store_uint32(v); }
static inline uint32_t load_threshold_from_flash(void)
{
    uint32_t tmp;
    return cfg_load_uint32(&tmp);
}

#endif /* __CONFIG_H__ */
