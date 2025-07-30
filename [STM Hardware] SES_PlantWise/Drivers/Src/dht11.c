#include "dht11.h"
#include "delay.h"
#include "stm32f4xx_hal.h"

#define DHT_PORT        GPIOA
#define DHT_PIN         GPIO_PIN_7
#define DHT_TIMEOUT_US  1000

/* Configure DHT pin as open-drain output with pull-up. */
static void set_output(void) {
    GPIO_InitTypeDef cfg = {0};
    cfg.Pin   = DHT_PIN;
    cfg.Mode  = GPIO_MODE_OUTPUT_OD;
    cfg.Pull  = GPIO_PULLUP;
    cfg.Speed = GPIO_SPEED_FREQ_LOW;
    HAL_GPIO_Init(DHT_PORT, &cfg);
}

/* Configure DHT pin as input with pull-up. */
static void set_input(void) {
    GPIO_InitTypeDef cfg = {0};
    cfg.Pin  = DHT_PIN;
    cfg.Mode = GPIO_MODE_INPUT;
    cfg.Pull = GPIO_PULLUP;
    HAL_GPIO_Init(DHT_PORT, &cfg);
}

/* Initialize DHT11 pin (clock enable, set high). */
void DHT11_Init(void) {
    __HAL_RCC_GPIOA_CLK_ENABLE();
    set_output();
    HAL_GPIO_WritePin(DHT_PORT, DHT_PIN, GPIO_PIN_SET);
}

/* Check sensor response: wait for 80탎 low then 80탎 high. */
static uint8_t DHT11_CheckResponse(void) {
    uint32_t t = 0;
    // Wait for LOW (~80 탎)
    while (HAL_GPIO_ReadPin(DHT_PORT, DHT_PIN) == GPIO_PIN_SET) {
        if (++t > DHT_TIMEOUT_US) return 0;
        delay_us(1);
    }
    t = 0;
    // Wait for HIGH (~80 탎)
    while (HAL_GPIO_ReadPin(DHT_PORT, DHT_PIN) == GPIO_PIN_RESET) {
        if (++t > DHT_TIMEOUT_US) return 0;
        delay_us(1);
    }
    return 1;
}

/* Read one byte from DHT11 by timing pulses. */
static uint8_t DHT11_ReadByte(void) {
    uint8_t val = 0;
    for (int i = 0; i < 8; i++) {
        uint32_t t = 0;
        // Wait for line to go HIGH (start of bit)
        while (HAL_GPIO_ReadPin(DHT_PORT, DHT_PIN) == GPIO_PIN_RESET) {
            if (++t > DHT_TIMEOUT_US) break;
            delay_us(1);
        }
        delay_us(40);  // Measure at 40탎 into the high pulse
        val <<= 1;
        if (HAL_GPIO_ReadPin(DHT_PORT, DHT_PIN) == GPIO_PIN_SET) {
            val |= 1;
        }
        // Wait for end of the high pulse
        t = 0;
        while (HAL_GPIO_ReadPin(DHT_PORT, DHT_PIN) == GPIO_PIN_SET) {
            if (++t > DHT_TIMEOUT_US) break;
            delay_us(1);
        }
    }
    return val;
}

/* Perform a full reading of temperature and humidity. */
DHT11_Status DHT11_Read(uint8_t *temperature, uint8_t *humidity) {
    uint8_t rh_i, rh_d, t_i, t_d, sum;

    /* Send start signal: pull LOW ~18ms then HIGH 40탎 */
    set_output();
    HAL_GPIO_WritePin(DHT_PORT, DHT_PIN, GPIO_PIN_RESET);
    delay_ms(18);
    HAL_GPIO_WritePin(DHT_PORT, DHT_PIN, GPIO_PIN_SET);
    delay_us(40);

    /* Switch to input and check sensor response */
    set_input();
    if (!DHT11_CheckResponse()) {
        DHT11_Init();
        return DHT11_ERR;
    }

    /* Read 5 bytes: humidity int, humidity dec, temp int, temp dec, checksum */
    rh_i = DHT11_ReadByte();
    rh_d = DHT11_ReadByte();
    t_i  = DHT11_ReadByte();
    t_d  = DHT11_ReadByte();
    sum  = DHT11_ReadByte();

    /* Verify checksum */
    if ((uint8_t)(rh_i + rh_d + t_i + t_d) != sum) {
        DHT11_Init();
        return DHT11_ERR;
    }

    *humidity    = rh_i;
    *temperature = t_i;
    DHT11_Init();
    return DHT11_OK;
}
