#include "main.h"
#include "stm32f4xx_hal.h"

/* Initialize timers (TIM1 is configured in main for delays) */
void timer_init(void) {
    // No additional timer setup needed here.
    // TIM1 is initialized in main (MX_TIM1_Init) for microsecond delays.
    // SysTick is already configured by HAL_Init for 1 ms ticks (used for HAL_Delay).
}
