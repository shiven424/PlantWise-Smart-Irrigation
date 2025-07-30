// main.c
#include "main.h"
#include "sensor.h"
#include "dht11.h"
#include "relay.h"
#include "servo.h"
#include "mqtt.h"
#include "timer.h"
#include "delay.h"
#include "config.h"
#include <stdio.h>
#include <string.h>
#include <stdarg.h>
#include "stm32f4xx_hal_pwr.h"

/* TIM1 is used for microsecond delays */
TIM_HandleTypeDef htim1;

/* ------------------------------------------------------------------------- */
/*                         Global flags & shared data                        */
/* ------------------------------------------------------------------------- */
volatile bool    s500_flag        = false;
volatile bool    h_flag           = false;
volatile bool    m_flag           = false;
volatile bool    config_flag      = false;
volatile bool    manual_flag      = false;
volatile bool    manual_stop_flag = false;

/* Pump/servo state and override timer */
volatile bool     watering        = false;
volatile uint32_t pump_timer_ms   = 0;     /* >0 ? running timed override   */

volatile uint32_t moisture_raw      = 0;
volatile uint32_t light_raw         = 0;
volatile float    moisture_percent  = 0.0f;
volatile float    light_percent     = 0.0f;
volatile uint8_t  dht_temp          = 0;
volatile uint8_t  dht_hum           = 0;
volatile uint32_t moisture_threshold = 50;  // default 50 %

extern ADC_HandleTypeDef  hadc1;
extern UART_HandleTypeDef huart2;

/* ------------------------------------------------------------------------- */
/*                          Debug printf over UART2                          */
/* ------------------------------------------------------------------------- */
#ifdef DEBUG_OFF
/* Debug switched off ? compile away every call in the file */
#define printf_uart(...)  ((void)0)
#else
static void printf_uart(const char *fmt, ...)
{
    extern volatile bool esp_raw_busy;          /* from mqtt.c */
    if (esp_raw_busy) return;

    char buf[128];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);

    if (n > 0) {
        if (n > (int)sizeof buf) n = sizeof buf;
        HAL_UART_Transmit(&huart2, (uint8_t *)buf, (uint16_t)n, HAL_MAX_DELAY);
    }
}
#endif

/* ------------------------------------------------------------------------- */
/*                         Board/peripheral helpers                          */
/* ------------------------------------------------------------------------- */

/* enter STOP mode until next interrupt ----------------------------- */
static void idle_sleep(void)
{
    /* all clocks run from PLL, so STOP will gate everything but SRAM */
    __HAL_PWR_CLEAR_FLAG(PWR_FLAG_WU);
    HAL_SuspendTick();                     /* stop SysTick during sleep */
    HAL_PWR_EnterSTOPMode(PWR_LOWPOWERREGULATOR_ON, PWR_STOPENTRY_WFI);
    HAL_ResumeTick();                      /* restart 1 ms tick         */

    /* re-configure clock after STOP (HAL API) */
    SystemClock_Config();
}

static void led_init(void)
{
    __HAL_RCC_GPIOD_CLK_ENABLE();
    GPIO_InitTypeDef g = {0};
    g.Pin  = LED_PIN;
    g.Mode = GPIO_MODE_OUTPUT_PP;
    g.Pull = GPIO_NOPULL;
    HAL_GPIO_Init(LED_GPIO_PORT, &g);
    HAL_GPIO_WritePin(LED_GPIO_PORT, LED_PIN, GPIO_PIN_RESET);
}

static void MX_TIM1_Init(void)
{
    __HAL_RCC_TIM1_CLK_ENABLE();
    htim1.Instance           = TIM1;
    htim1.Init.Prescaler     = (SystemCoreClock / 1000000) - 1;  // 1 µs tick
    htim1.Init.CounterMode   = TIM_COUNTERMODE_UP;
    htim1.Init.Period        = 0xFFFF;
    htim1.Init.ClockDivision = TIM_CLOCKDIVISION_DIV1;
    HAL_TIM_Base_Init(&htim1);
    HAL_TIM_Base_Start(&htim1);
}

/* ------------------------------------------------------------------------- */
/*                                   main                                    */
/* ------------------------------------------------------------------------- */
int main(void)
{
    HAL_Init();

    /* restore threshold from flash (default 50 % if not set) */
    cfg_load_uint32(&moisture_threshold);
    if (moisture_threshold == 0xFFFFFFFF || moisture_threshold == 0)
        moisture_threshold = 50;
		
    SystemClock_Config();

    /* peripherals --------------------------------------------------------- */
    DHT11_Init();
    relay_init();                // pump OFF at start-up
    servo_init();                // servo idle
    wifi_init();
    mqtt_init();
    timer_init();                // SysTick + 500 ms / hourly flags
    MX_TIM1_Init();
    led_init();

    /* tell the outside world we are alive */
    mqtt_publish(TOPIC_NOTIFICATION, "online");

    /* --------------------------------------------------------------------- */
    /*                                loop                                   */
    /* --------------------------------------------------------------------- */
    bool adc_ready = false;
    while (1) {
        /* ----------------------- 500 ms cycle ---------------------------- */
        if (s500_flag) {
            s500_flag = false;
            HAL_GPIO_TogglePin(LED_GPIO_PORT, LED_PIN);

            /* -- read DHT11 every second (tick counts 500 ms slices) ------ */
            if (adc_ready) {                // ADC must be disabled for DHT
                HAL_ADC_Stop(&hadc1);
                HAL_ADC_DeInit(&hadc1);
                __HAL_RCC_ADC1_CLK_DISABLE();
                adc_ready = false;
            }
            static uint8_t tick = 0;
            if (++tick >= (1000 / SENSOR_UPDATE_MS)) {
                tick = 0;
                uint8_t T = 0, H = 0;
                if (DHT11_Read(&T, &H) == DHT11_OK) {
                    dht_temp = 2*T;
                    dht_hum  = H/2;
                }
            }

            /* -- read soil-moisture & LDR via ADC ------------------------- */
            if (!adc_ready) {
                __HAL_RCC_ADC1_CLK_ENABLE();
                sensor_init();
                adc_ready = true;
            }
            HAL_ADC_Start(&hadc1);
            if (HAL_ADC_PollForConversion(&hadc1, 10) == HAL_OK)
                moisture_raw = HAL_ADC_GetValue(&hadc1);
            if (HAL_ADC_PollForConversion(&hadc1, 10) == HAL_OK)
                light_raw = HAL_ADC_GetValue(&hadc1);
            HAL_ADC_Stop(&hadc1);

            /* -- convert + debug ------------------------------------------ */
            moisture_percent = CalibratedMoisture(moisture_raw);
            light_percent    = Sensor_ConvertToPercentage(light_raw);
            printf_uart("DBG: thresh=%lu   moist=%.1f%%   timer=%lu\r\n",
                        moisture_threshold, moisture_percent, pump_timer_ms);

            /* -- publish JSON payload ------------------------------------- */
            {
                char payload[128];
                int n = snprintf(payload, sizeof payload,
                    "{\"moisture\":%.1f,\"light\":%.1f,\"temp\":%u,\"hum\":%u}",
                    moisture_percent, light_percent, dht_temp, dht_hum);
                if (n > 0 && n < (int)sizeof(payload))
                    mqtt_publish_raw(TOPIC_SENSORS, payload, 0);
            }

            /* -- automatic irrigation (disabled while override active) ---- */
            if (pump_timer_ms == 0) {
                if (moisture_percent < (float)moisture_threshold) {
                    if (!watering) {
                        watering = true;
                        relay_set(true);
                        servo_start_sweep();
                        mqtt_publish(TOPIC_NOTIFICATION, "watering_start");
                    }
                } else {
                    if (watering) {
                        watering = false;
                        relay_set(false);
                        servo_stop();
                        mqtt_publish(TOPIC_NOTIFICATION, "watering_stop");
                    }
                }
            }
        } /* 500 ms slice */

        /* -------------------------- hourly slice ------------------------- */
        if (h_flag) {
            h_flag = false;
            if (pump_timer_ms == 0) {       // ignore if override running
                if (moisture_percent < (float)moisture_threshold) {
                    if (!watering) {
                        watering = true;
                        relay_set(true);
                        servo_start_sweep();
                        mqtt_publish(TOPIC_NOTIFICATION, "watering_start");
                    }
                } else {
                    if (watering) {
                        watering = false;
                        relay_set(false);
                        servo_stop();
                        mqtt_publish(TOPIC_NOTIFICATION, "watering_stop");
                    }
                }
            }
        }

        /* ------------------- config threshold from MQTT ------------------ */
        if (config_flag) {
            config_flag = false;
            printf_uart("[main] cfg_flag handled, thresh=%lu\r\n",
                        moisture_threshold);

            cfg_store_uint32(moisture_threshold);  // save to flash

            char ack[64];
            snprintf(ack, sizeof ack,
                     "{\"threshold_ack\":%lu}", moisture_threshold);
            mqtt_publish_raw(TOPIC_CONFIG_ACK, ack, 1);
        }

        /* ---------------- manual start / stop (MQTT) --------------------- */
        if (manual_flag) {
            manual_flag = false;
            if (!watering) {
                watering = true;
                relay_set(true);
                servo_start_sweep();
                mqtt_publish(TOPIC_NOTIFICATION, "watering_start");
            }
            /* do NOT touch pump_timer_ms – mqtt.c has already set it */
        }

        if (manual_stop_flag) {
            manual_stop_flag = false;
            if (watering) {
                watering = false;
                relay_set(false);
                servo_stop();
                mqtt_publish(TOPIC_NOTIFICATION, "watering_stop");
            }
            pump_timer_ms = 0;              // cancel any remaining countdown
        }

        /* ---------- let MQTT parser chew incoming UART bytes ------------- */
        mqtt_process_uart();
    }
}

/* ------------------------------------------------------------------------- */
/*                       System clock & error handler                        */
/* ------------------------------------------------------------------------- */
void SystemClock_Config(void)
{
    RCC_OscInitTypeDef RCC_OscInitStruct = {0};
    RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};

    __HAL_RCC_PWR_CLK_ENABLE();
    HAL_PWR_EnableBkUpAccess();

    RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSE;
    RCC_OscInitStruct.HSEState       = RCC_HSE_ON;
    RCC_OscInitStruct.PLL.PLLState   = RCC_PLL_ON;
    RCC_OscInitStruct.PLL.PLLSource  = RCC_PLLSOURCE_HSE;
    RCC_OscInitStruct.PLL.PLLM       = 8;
    RCC_OscInitStruct.PLL.PLLN       = 336;
    RCC_OscInitStruct.PLL.PLLP       = RCC_PLLP_DIV2;
    RCC_OscInitStruct.PLL.PLLQ       = 7;
    if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
        Error_Handler();

    RCC_ClkInitStruct.ClockType      = RCC_CLOCKTYPE_SYSCLK   |
                                       RCC_CLOCKTYPE_HCLK     |
                                       RCC_CLOCKTYPE_PCLK1    |
                                       RCC_CLOCKTYPE_PCLK2;
    RCC_ClkInitStruct.SYSCLKSource   = RCC_SYSCLKSOURCE_PLLCLK;
    RCC_ClkInitStruct.AHBCLKDivider  = RCC_SYSCLK_DIV1;
    RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV4;
    RCC_ClkInitStruct.APB2CLKDivider = RCC_HCLK_DIV2;
    if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_5) != HAL_OK)
        Error_Handler();
}

void Error_Handler(void)
{
    __disable_irq();
    while (1) {
        /* optional: blink LED for fatal error */
    }
}
