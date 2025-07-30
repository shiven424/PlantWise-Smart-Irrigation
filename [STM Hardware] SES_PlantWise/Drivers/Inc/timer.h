#ifndef __TIMER_H
#define __TIMER_H

#include "main.h"

/* Initialize SysTick for 1ms interrupts and any additional hardware timers */
void timer_init(void);

/* SysTick interrupt handler (typically provided by HAL, but we override to set flags) */
void SysTick_Handler(void);

#endif /* __TIMER_H */
