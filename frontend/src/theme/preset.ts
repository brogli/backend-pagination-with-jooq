import { definePreset } from '@primeuix/themes'
import Aura from '@primeuix/themes/aura'

// Project preset — extends PrimeVue's Aura.
// Add design-token overrides below. See https://primevue.org/theming/styled/ for the full token schema.
export const AppPreset = definePreset(Aura, {
  semantic: {
    // Example override: uncomment and adapt to switch the primary color ramp.
    // primary: {
    //   50: '{emerald.50}',
    //   100: '{emerald.100}',
    //   200: '{emerald.200}',
    //   300: '{emerald.300}',
    //   400: '{emerald.400}',
    //   500: '{emerald.500}',
    //   600: '{emerald.600}',
    //   700: '{emerald.700}',
    //   800: '{emerald.800}',
    //   900: '{emerald.900}',
    //   950: '{emerald.950}',
    // },
  },
})
