#ifndef __SENSOR_H__
#define __SENSOR_H__

#include "stm32f4xx_hal.h"
#include <stdint.h>

/* ADC handle for ADC1 (soil moisture & light) */
extern ADC_HandleTypeDef hadc1;

/**
 * @brief  Initialize ADC1 to read soil moisture (IN0) & light (IN1).
 */
void sensor_init(void);

/**
 * @brief  (Optional) trigger an interrupt-driven ADC conversion of both channels.
 */
void sensor_read_all(void);

/**
 * @brief  Convert raw ADC (0–4095) to 0–100 %.
 * @param  raw_value  raw ADC reading
 * @return percentage
 */
float Sensor_ConvertToPercentage(uint32_t raw_value);

float CalibratedMoisture(uint32_t raw_value);
#endif /* __SENSOR_H__ */
