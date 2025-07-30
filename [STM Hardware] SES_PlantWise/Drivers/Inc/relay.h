#ifndef __RELAY_H
#define __RELAY_H

#include <stdbool.h>
#include "main.h"

void relay_init(void);          /* GPIO + start OFF          */
void relay_set(bool on);        /* true = pump ON, false OFF */

#endif /* __RELAY_H */
